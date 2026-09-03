package com.smart.agent.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.conversation.Conversation;
import com.smart.agent.conversation.ConversationRepository;
import com.smart.agent.conversation.ConversationService;
import com.smart.agent.run.AgentRun;
import com.smart.agent.run.AgentRunRepository;
import com.smart.agent.run.AgentRunService;
import com.smart.agent.run.AgentRunStatus;
import com.smart.agent.run.AgentRunStep;
import com.smart.agent.run.AgentRunStepRepository;
import com.smart.agent.security.AgentUserContext;
import com.smart.agent.model.ModelGateway;
import com.smart.agent.model.ModelEvent;
import com.smart.agent.model.ModelRequest;
import com.smart.agent.knowledge.KnowledgeCitation;
import com.smart.agent.knowledge.KnowledgeSearchService;
import com.smart.agent.tool.ToolExecutor;
import com.smart.agent.tool.ToolRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.Disposable;
import org.springframework.http.MediaType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "agent.persistence.enabled=false",
        "AGENT_LOCAL_CONTEXT_SECRET=task-8-test-context-secret"
})
@Import(ChatControllerIT.TestBeans.class)
class ChatControllerIT {
    private static final String CONTEXT_SECRET = "task-8-test-context-secret";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private InMemoryAgentRunRepository runRepository;

    @Autowired
    private ChatOrchestrator orchestrator;

    @Autowired
    private TestBeans.ScenarioModelGateway scenarioModelGateway;
    @Autowired private AgentRunService agentRunService;
    @Autowired private ToolRegistry toolRegistry;
    @Autowired private ToolExecutor toolExecutor;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private KnowledgeSearchService knowledgeSearchService;
    @Autowired private InMemoryAgentRunStepRepository stepRepository;

    private String conversationId;

    @BeforeEach
    void createConversation() {
        runRepository.clear();
        scenarioModelGateway.reset();
        conversationId = conversationService.create("tenant-1", "user-1", "项目问答").id();
    }

    @Test
    void streamsProjectToolAnswerAndPersistsAuditTrail() {
        List<String> events = webTestClient.post().uri("/agent/chat/stream")
                .header("X-Agent-Context", signedContextToken(
                        Set.of("project:read"), Set.of("project-1"), Set.of("space-1")))
                .bodyValue(Map.of(
                        "conversationId", conversationId,
                        "question", "查询项目 project-1 概况",
                        "pageContext", Map.of("projectId", "project-1")))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class)
                .getResponseBody()
                .collectList()
                .block(Duration.ofSeconds(5));

        assertThat(events).isNotNull();
        assertThat(events.getFirst()).contains("message_start");
        assertThat(events.getLast()).contains("message_end");
        assertThat(events.getFirst()).contains("traceId");
        assertThat(events).anyMatch(event -> event.contains("tool_start"));
        assertThat(events).anyMatch(event -> event.contains("tool_result"));
        assertThat(indexOf(events, "tool_start")).isLessThan(indexOf(events, "tool_result"));
        AgentRun run = runRepository.findByTenantIdAndUserIdAndConversationId("tenant-1", "user-1", conversationId).getFirst();
        assertThat(run.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(run.traceId()).isNotBlank();
        assertThat(run.toolExecutionSummaries()).contains("project.getOverview").doesNotContain("projectName");
        assertThat(run.inputTokens()).isPositive();
        assertThat(run.outputTokens()).isPositive();
    }

    @Test
    void rejectsUntrustedIdentityAndPermissionClaims() {
        webTestClient.post().uri("/agent/chat/stream")
                .header("X-Agent-Context", signedContextToken(Set.of(), Set.of(), Set.of()))
                .bodyValue(Map.of(
                        "conversationId", conversationId,
                        "question", "hello",
                        "tenantId", "attacker",
                        "permissions", List.of("project:read")))
                .exchange()
                .expectStatus().isBadRequest();
        assertThat(runRepository.findByTenantIdAndUserIdAndConversationId("tenant-1", "user-1", conversationId)).isEmpty();
    }

    @Test
    void persistsPermissionDenialWithoutLeakingDetails() {
        List<String> events = webTestClient.post().uri("/agent/chat/stream")
                .header("X-Agent-Context", signedContextToken(Set.of(), Set.of(), Set.of()))
                .bodyValue(Map.of(
                        "conversationId", conversationId,
                        "question", "hello",
                        "pageContext", Map.of("projectId", "project-1")))
                .exchange().expectStatus().isOk()
                .returnResult(String.class).getResponseBody().collectList().block(Duration.ofSeconds(5));

        assertThat(events).anySatisfy(event -> assertThat(event)
                .contains("AGENT_PROJECT_FORBIDDEN").doesNotContain("Project is not permitted"));
        AgentRun run = runRepository.findByTenantIdAndUserIdAndConversationId("tenant-1", "user-1", conversationId).getFirst();
        assertThat(run.status()).isEqualTo(AgentRunStatus.PERMISSION_DENIED);
        assertThat(run.safeErrorCode()).isEqualTo("AGENT_PROJECT_FORBIDDEN");
    }

    @Test
    void emitsTrustedCitationBeforeAnswerDeltaAndPersistsIt() {
        List<String> events = webTestClient.post().uri("/agent/chat/stream")
                .header("X-Agent-Context", signedContextToken(
                        Set.of("knowledge:read"), Set.of("project-1"), Set.of("space-1")))
                .bodyValue(Map.of("conversationId", conversationId, "question", "查询知识文档规范",
                        "pageContext", Map.of("projectId", "project-1")))
                .exchange().expectStatus().isOk().returnResult(String.class)
                .getResponseBody().collectList().block(Duration.ofSeconds(5));

        assertThat(indexOf(events, "citation")).as("events=%s", events).isGreaterThanOrEqualTo(0)
                .isLessThan(indexOf(events, "message_delta"));
        AgentRun run = runRepository.findByTenantIdAndUserIdAndConversationId("tenant-1", "user-1", conversationId).getFirst();
        assertThat(run.citationSummaries()).contains("citation-token").doesNotContain("sensitive excerpt");
    }

    @Test
    void malformedToolInputEndsWithSafeFailure() {
        List<String> events = stream("malformed-tool", Set.of("project:read"), Set.of("project-1"));
        assertThat(events.getLast()).contains("AGENT_TOOL_INVALID_INPUT").doesNotContain("not-json");
        AgentRun run = runRepository.findByTenantIdAndUserIdAndConversationId(
                "tenant-1", "user-1", conversationId).getFirst();
        assertThat(run.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(stepRepository.findByTenantIdAndUserIdAndRunIdOrderBySequence(
                "tenant-1", "user-1", run.id())).extracting(AgentRunStep::safeOutputSummary)
                .anySatisfy(summary -> assertThat(summary).contains("INVALID_INPUT").doesNotContain("not-json"));
    }

    @Test
    void modelTimeoutIsPersistedAndStreamedSafely() {
        List<String> events = stream("model-timeout", Set.of(), Set.of());
        assertThat(events.getLast()).contains("AGENT_MODEL_TIMEOUT").doesNotContain("provider secret");
        assertThat(runRepository.findByTenantIdAndUserIdAndConversationId("tenant-1", "user-1", conversationId).getFirst().status())
                .isEqualTo(AgentRunStatus.TIMEOUT);
    }

    @Test
    void enforcesFiveToolCallLimit() {
        List<String> events = stream("tool-limit", Set.of("project:read"), Set.of("project-1"));
        assertThat(events.getLast()).contains("AGENT_TOOL_CALL_LIMIT");
        AgentRun run = runRepository.findByTenantIdAndUserIdAndConversationId("tenant-1", "user-1", conversationId).getFirst();
        assertThat(run.toolExecutionSummaries().lines()).hasSize(5);
    }

    @Test
    void executesMultipleToolsInArrivalOrderAndCountsEveryModelTurn() {
        List<String> events = stream("multi-tools", Set.of("project:read"), Set.of("project-1"));
        assertThat(events.stream().filter(event -> event.contains("\"type\":\"tool_start\"")).toList())
                .hasSize(2);
        AgentRun run = runRepository.findByTenantIdAndUserIdAndConversationId("tenant-1", "user-1", conversationId).getFirst();
        assertThat(run.inputTokens()).isEqualTo(10);
        assertThat(run.outputTokens()).isEqualTo(12);
        assertThat(run.toolExecutionSummaries().lines()).hasSize(2);
        assertThat(run.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(stepRepository.findByTenantIdAndUserIdAndRunIdOrderBySequence(
                "tenant-1", "user-1", run.id())).extracting(AgentRunStep::type)
                .containsExactly("MODEL", "TOOL", "TOOL", "MODEL", "TERMINAL");
        assertThat(scenarioModelGateway.sawTypedToolResult()).isTrue();
    }

    @Test
    void capsCitationsAtTwenty() {
        List<String> events = webTestClient.post().uri("/agent/chat/stream")
                .header("X-Agent-Context", signedContextToken(
                        Set.of("knowledge:read"), Set.of("project-1"), Set.of("space-1")))
                .bodyValue(Map.of("conversationId", conversationId, "question", "many citation 文档",
                        "pageContext", Map.of("projectId", "project-1")))
                .exchange().returnResult(String.class).getResponseBody().collectList().block(Duration.ofSeconds(5));
        assertThat(events.stream().filter(event -> event.contains("\"type\":\"citation\"")).toList()).hasSize(20);
    }

    @Test
    void clientCancellationPersistsCancelledTerminalState() {
        orchestrator.stream(new ChatCommand(conversationId, "never-completes", null),
                        new AgentUserContext("tenant-1", "user-1", "identity-1", Set.of(), Set.of(), Set.of()),
                        "trace-cancel")
                .take(4).blockLast(Duration.ofSeconds(5));
        awaitStatus(AgentRunStatus.CANCELLED);
        assertThat(scenarioModelGateway.subscriptions()).isZero();
        Conversation conversation = conversationService.find("tenant-1", "user-1", conversationId);
        assertThat(conversation.messages()).extracting(com.smart.agent.conversation.Message::role)
                .containsExactly(com.smart.agent.conversation.Message.Role.USER);
        AgentRun run = runRepository.findByTenantIdAndUserIdAndConversationId("tenant-1", "user-1", conversationId).getFirst();
        assertThat(run.toolExecutionSummaries()).isEmpty();
        assertThat(run.inputTokens()).isZero();
    }

    @Test
    void forwardsFirstDeltaBeforeModelCompletion() {
        Flux<ChatEvent> events = orchestrator.stream(new ChatCommand(conversationId, "slow-stream", null),
                new AgentUserContext("tenant-1", "user-1", "identity-1", Set.of(), Set.of(), Set.of()),
                "trace-stream");
        AtomicBoolean deltaBeforeComplete = new AtomicBoolean();
        List<ChatEvent> streamed = events.doOnNext(event -> {
            if (event.type().equals("message_delta") && !scenarioModelGateway.completedEmitted()) {
                deltaBeforeComplete.set(true);
            }
        }).collectList().block(Duration.ofSeconds(5));
        assertThat(deltaBeforeComplete).isTrue();
        assertThat(streamed.getLast().type()).isEqualTo("message_end");
    }

    @Test
    void cancellationAfterDeltaDisposesModelAndPreventsLaterPersistence() {
        orchestrator.stream(new ChatCommand(conversationId, "cancel-after-delta", null),
                        new AgentUserContext("tenant-1", "user-1", "identity-1", Set.of(), Set.of(), Set.of()),
                        "trace-cancel-active")
                .take(5).blockLast(Duration.ofSeconds(5));
        awaitStatus(AgentRunStatus.CANCELLED);
        assertThat(scenarioModelGateway.subscriptions()).isOne();
        assertThat(scenarioModelGateway.disposed()).isTrue();
        assertThat(conversationService.find("tenant-1", "user-1", conversationId).messages())
                .extracting(com.smart.agent.conversation.Message::role)
                .containsExactly(com.smart.agent.conversation.Message.Role.USER);
        AgentRun run = runRepository.findByTenantIdAndUserIdAndConversationId(
                "tenant-1", "user-1", conversationId).getFirst();
        assertThat(run.inputTokens()).isZero();
        assertThat(run.toolExecutionSummaries()).isEmpty();
    }

    @Test
    void absoluteDeadlineStopsBlockingKnowledgeAndPersistsTimeout() {
        ChatOrchestrator shortBudget = new ChatOrchestrator(conversationService, agentRunService,
                scenarioModelGateway, toolRegistry, toolExecutor, objectMapper, knowledgeSearchService,
                Duration.ofMillis(30));
        long started = System.nanoTime();
        List<ChatEvent> events = shortBudget.stream(new ChatCommand(conversationId, "slow-budget 文档",
                        new ChatCommand.PageContext(null, "project-1", null, null)),
                new AgentUserContext("tenant-1", "user-1", "identity-1", Set.of("knowledge:read"),
                        Set.of("project-1"), Set.of("space-1")), "trace-timeout")
                .collectList().block(Duration.ofSeconds(2));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(500));
        assertThat(events.getLast().code()).isEqualTo("AGENT_RUN_TIMEOUT");
        assertThat(runRepository.findByTenantIdAndUserIdAndConversationId("tenant-1", "user-1", conversationId)
                .getFirst().status()).isEqualTo(AgentRunStatus.TIMEOUT);
    }

    @Test
    void doesNotEmitSuccessfulTerminalEventWhenAtomicCompletionPersistenceFails() {
        AgentRunService failingPersistence = spy(agentRunService);
        doThrow(new IllegalStateException("database detail"))
                .when(failingPersistence).completeWithAssistant(any(), any(), any(), any(), any(), any(),
                        anyInt(), anyInt(), any(), any());
        ChatOrchestrator failing = new ChatOrchestrator(conversationService, failingPersistence,
                scenarioModelGateway, toolRegistry, toolExecutor, objectMapper, knowledgeSearchService);

        List<ChatEvent> events = failing.stream(new ChatCommand(conversationId, "hello", null),
                        new AgentUserContext("tenant-1", "user-1", "identity-1", Set.of(), Set.of(), Set.of()),
                        "trace-persistence")
                .collectList().block(Duration.ofSeconds(5));

        assertThat(events).extracting(ChatEvent::type).doesNotContain("message_end");
        assertThat(events.getLast().type()).isEqualTo("error");
        assertThat(events.getLast().code()).isEqualTo("AGENT_PERSISTENCE_FAILED");
        assertThat(events.getLast().text()).doesNotContain("database detail");
        assertThat(conversationService.find("tenant-1", "user-1", conversationId).messages())
                .extracting(com.smart.agent.conversation.Message::role)
                .containsExactly(com.smart.agent.conversation.Message.Role.USER);
    }

    @Test
    void cancellationAtToolStartWinsBeforeToolInvocationAndLeavesNoToolAudit() {
        AtomicInteger invocations = new AtomicInteger();
        ToolExecutor observedExecutor = spy(toolExecutor);
        doAnswer(invocation -> {
            invocations.incrementAndGet();
            return invocation.callRealMethod();
        }).when(observedExecutor).execute(any(), any(), any());
        ChatOrchestrator observed = new ChatOrchestrator(conversationService, agentRunService,
                scenarioModelGateway, toolRegistry, observedExecutor, objectMapper, knowledgeSearchService);

        observed.stream(new ChatCommand(conversationId, "multi-tools", null),
                        new AgentUserContext("tenant-1", "user-1", "identity-1", Set.of("project:read"),
                                Set.of("project-1"), Set.of()), "trace-tool-race")
                .takeUntil(event -> event.type().equals("tool_start"))
                .blockLast(Duration.ofSeconds(5));

        awaitStatus(AgentRunStatus.CANCELLED);
        assertThat(invocations).hasValue(0);
        AgentRun run = runRepository.findByTenantIdAndUserIdAndConversationId(
                "tenant-1", "user-1", conversationId).getFirst();
        assertThat(run.toolExecutionSummaries()).isEmpty();
        assertThat(conversationService.find("tenant-1", "user-1", conversationId).messages())
                .extracting(com.smart.agent.conversation.Message::role)
                .containsExactly(com.smart.agent.conversation.Message.Role.USER);
    }

    @Test
    void callbackAuditBudgetExpiryPersistsTimeoutInsteadOfLeavingGenerating() {
        stepRepository.delayNextSave(Duration.ofMillis(150));
        ChatOrchestrator shortBudget = new ChatOrchestrator(conversationService, agentRunService,
                scenarioModelGateway, toolRegistry, toolExecutor, objectMapper, knowledgeSearchService,
                Duration.ofMillis(40));

        List<ChatEvent> events = shortBudget.stream(new ChatCommand(conversationId, "multi-tools", null),
                        new AgentUserContext("tenant-1", "user-1", "identity-1", Set.of("project:read"),
                                Set.of("project-1"), Set.of()), "trace-callback-timeout")
                .collectList().block(Duration.ofSeconds(5));

        assertThat(events.getLast().code()).isEqualTo("AGENT_RUN_TIMEOUT");
        assertThat(runRepository.findByTenantIdAndUserIdAndConversationId(
                "tenant-1", "user-1", conversationId).getFirst().status()).isEqualTo(AgentRunStatus.TIMEOUT);
    }

    @Test
    void cancellationInterruptsAnAlreadyStartedToolAndSuppressesItsResult() throws Exception {
        CountDownLatch toolStarted = new CountDownLatch(1);
        CountDownLatch toolInterrupted = new CountDownLatch(1);
        AtomicInteger invocations = new AtomicInteger();
        ToolExecutor blockingExecutor = spy(toolExecutor);
        doAnswer(invocation -> {
            invocations.incrementAndGet();
            toolStarted.countDown();
            try {
                new CountDownLatch(1).await();
                return null;
            } catch (InterruptedException interrupted) {
                toolInterrupted.countDown();
                Thread.currentThread().interrupt();
                return Map.of("ignored", true);
            }
        }).when(blockingExecutor).execute(any(), any(), any());
        ChatOrchestrator observed = new ChatOrchestrator(conversationService, agentRunService,
                scenarioModelGateway, toolRegistry, blockingExecutor, objectMapper, knowledgeSearchService);

        Disposable subscription = observed.stream(new ChatCommand(conversationId, "multi-tools", null),
                        new AgentUserContext("tenant-1", "user-1", "identity-1", Set.of("project:read"),
                                Set.of("project-1"), Set.of()), "trace-active-tool-cancel")
                .subscribe();
        assertThat(toolStarted.await(2, TimeUnit.SECONDS)).isTrue();
        subscription.dispose();

        assertThat(toolInterrupted.await(2, TimeUnit.SECONDS)).isTrue();
        awaitStatus(AgentRunStatus.CANCELLED);
        assertThat(invocations).hasValue(1);
        AgentRun run = runRepository.findByTenantIdAndUserIdAndConversationId(
                "tenant-1", "user-1", conversationId).getFirst();
        assertThat(run.toolExecutionSummaries()).isEmpty();
        assertThat(conversationService.find("tenant-1", "user-1", conversationId).messages())
                .extracting(com.smart.agent.conversation.Message::role)
                .containsExactly(com.smart.agent.conversation.Message.Role.USER);
    }

    @Test
    void cancellationAfterToolReturnsButBeforeAuditSuppressesEveryLaterSideEffect() throws Exception {
        CountDownLatch resultReturned = new CountDownLatch(1);
        CountDownLatch releaseSerialization = new CountDownLatch(1);
        CountDownLatch serializationFinished = new CountDownLatch(1);
        CountDownLatch toolAuditAttempted = new CountDownLatch(1);
        ObjectMapper gatedMapper = spy(objectMapper);
        doAnswer(invocation -> {
            resultReturned.countDown();
            assertThat(releaseSerialization.await(2, TimeUnit.SECONDS)).isTrue();
            Object serialized = invocation.callRealMethod();
            serializationFinished.countDown();
            return serialized;
        }).when(gatedMapper).writeValueAsString(any());
        AgentRunService observedRuns = spy(agentRunService);
        doAnswer(invocation -> {
            if (invocation.getArgument(5) != null) toolAuditAttempted.countDown();
            return invocation.callRealMethod();
        }).when(observedRuns).recordAudit(any(), any(), any(), anyInt(), anyInt(), any(), any(), any());
        ChatOrchestrator observed = new ChatOrchestrator(conversationService, observedRuns,
                scenarioModelGateway, toolRegistry, toolExecutor, gatedMapper, knowledgeSearchService);

        Disposable subscription = observed.stream(new ChatCommand(conversationId, "multi-tools", null),
                        new AgentUserContext("tenant-1", "user-1", "identity-1", Set.of("project:read"),
                                Set.of("project-1"), Set.of()), "trace-post-tool-cancel")
                .subscribe();
        assertThat(resultReturned.await(2, TimeUnit.SECONDS)).isTrue();
        subscription.dispose();
        releaseSerialization.countDown();
        assertThat(serializationFinished.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(toolAuditAttempted.await(500, TimeUnit.MILLISECONDS)).isFalse();

        awaitStatus(AgentRunStatus.CANCELLED);
        AgentRun run = runRepository.findByTenantIdAndUserIdAndConversationId(
                "tenant-1", "user-1", conversationId).getFirst();
        assertThat(run.toolExecutionSummaries()).isEmpty();
        assertThat(stepRepository.findByTenantIdAndUserIdAndRunIdOrderBySequence(
                "tenant-1", "user-1", run.id())).extracting(AgentRunStep::type)
                .containsExactly("MODEL", "TERMINAL");
        assertThat(scenarioModelGateway.modelCalls()).isOne();
        assertThat(conversationService.find("tenant-1", "user-1", conversationId).messages())
                .extracting(com.smart.agent.conversation.Message::role)
                .containsExactly(com.smart.agent.conversation.Message.Role.USER);
    }

    @Test
    void rejectsCompletedOnlyAnswerOverUtf8LimitBeforeDeltaOrPersistence() {
        List<String> events = stream("oversized-completed", Set.of(), Set.of());

        assertThat(events).noneMatch(event -> event.contains("message_delta"));
        assertThat(events.getLast()).contains("AGENT_ANSWER_TOO_LARGE");
        AgentRun run = runRepository.findByTenantIdAndUserIdAndConversationId(
                "tenant-1", "user-1", conversationId).getFirst();
        assertThat(run.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(conversationService.find("tenant-1", "user-1", conversationId).messages())
                .extracting(com.smart.agent.conversation.Message::role)
                .containsExactly(com.smart.agent.conversation.Message.Role.USER);
    }

    private List<String> stream(String question, Set<String> permissions, Set<String> projectIds) {
        return webTestClient.post().uri("/agent/chat/stream")
                .header("X-Agent-Context", signedContextToken(permissions, projectIds, Set.of()))
                .bodyValue(Map.of("conversationId", conversationId, "question", question))
                .exchange().expectStatus().isOk().returnResult(String.class)
                .getResponseBody().collectList().block(Duration.ofSeconds(5));
    }

    private void awaitStatus(AgentRunStatus expected) {
        for (int i = 0; i < 50; i++) {
            List<AgentRun> runs = runRepository.findByTenantIdAndUserIdAndConversationId("tenant-1", "user-1", conversationId);
            if (!runs.isEmpty() && runs.getFirst().status() == expected) return;
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        assertThat(runRepository.findByTenantIdAndUserIdAndConversationId("tenant-1", "user-1", conversationId).getFirst().status()).isEqualTo(expected);
    }

    private static int indexOf(List<String> events, String type) {
        for (int index = 0; index < events.size(); index++) {
            if (events.get(index).contains(type)) return index;
        }
        return -1;
    }

    private static String signedContextToken(
            Set<String> permissions, Set<String> projectIds, Set<String> knowledgeSpaceIds) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            byte[] payload = mapper.writeValueAsBytes(Map.of(
                    "tenantId", "tenant-1",
                    "userId", "user-1",
                    "identityId", "identity-1",
                    "permissions", permissions,
                    "projectIds", projectIds,
                    "knowledgeSpaceIds", knowledgeSpaceIds,
                    "exp", Instant.now().plusSeconds(300).getEpochSecond()));
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(CONTEXT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
            return encodedPayload + "." + signature;
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean
        InMemoryConversationRepository conversationRepository() {
            return new InMemoryConversationRepository();
        }

        @Bean
        ConversationService conversationService(InMemoryConversationRepository repository) {
            return new ConversationService(repository);
        }

        @Bean
        InMemoryAgentRunRepository agentRunRepository() {
            return new InMemoryAgentRunRepository();
        }

        @Bean
        InMemoryAgentRunStepRepository agentRunStepRepository() {
            return new InMemoryAgentRunStepRepository();
        }

        @Bean
        AgentRunService agentRunService(
                InMemoryAgentRunRepository runs, InMemoryConversationRepository conversations,
                InMemoryAgentRunStepRepository steps) {
            return new AgentRunService(runs, conversations, steps);
        }

        @Bean(destroyMethod = "close")
        ToolExecutor toolExecutor(ToolRegistry registry, AgentRunService runService) {
            return new ToolExecutor(registry, runService);
        }

        @Bean
        ChatOrchestrator chatOrchestrator(
                ConversationService conversations,
                AgentRunService runs,
                ModelGateway modelGateway,
                ToolRegistry registry,
                ToolExecutor executor,
                ObjectMapper objectMapper, KnowledgeSearchService knowledgeSearchService) {
            return new ChatOrchestrator(conversations, runs, modelGateway, registry, executor, objectMapper,
                    knowledgeSearchService);
        }

        @Bean
        @Primary
        ScenarioModelGateway scenarioModelGateway() {
            return new ScenarioModelGateway();
        }

        static final class ScenarioModelGateway implements ModelGateway {
            private final AtomicInteger subscriptions = new AtomicInteger();
            private final AtomicBoolean disposed = new AtomicBoolean();
            private final AtomicBoolean completedEmitted = new AtomicBoolean();
            private final AtomicBoolean typedToolResult = new AtomicBoolean();
            private final AtomicInteger modelCalls = new AtomicInteger();

            @Override
            public Flux<ModelEvent> stream(ModelRequest request) {
                modelCalls.incrementAndGet();
                String question = request.redactedConversationMessages().getFirst().content();
                if (question.equals("malformed-tool")) {
                    return Flux.just(new ModelEvent.ToolRequested("bad", "project.getOverview", "not-json"));
                }
                if (question.equals("model-timeout")) {
                    return Flux.just(new ModelEvent.Failed("MODEL_TIMEOUT", "provider secret"));
                }
                if (question.equals("never-completes")) return Flux.<ModelEvent>never()
                        .doOnSubscribe(ignored -> subscriptions.incrementAndGet())
                        .doOnCancel(() -> disposed.set(true));
                if (question.equals("cancel-after-delta")) return Flux.concat(
                                Flux.just(new ModelEvent.TextDelta("partial")), Flux.<ModelEvent>never())
                        .doOnSubscribe(ignored -> subscriptions.incrementAndGet())
                        .doOnCancel(() -> disposed.set(true));
                if (question.equals("slow-stream")) return Flux.concat(
                        Flux.just(new ModelEvent.TextDelta("first")),
                        Mono.delay(Duration.ofMillis(100)).map(ignored -> (ModelEvent) new ModelEvent.Completed(
                                "first", 3, 4)).doOnNext(ignored -> completedEmitted.set(true)));
                if (question.equals("tool-limit")) {
                    return Flux.just(new ModelEvent.ToolRequested("repeat", "project.getOverview",
                            "{\"projectId\":\"project-1\"}"));
                }
                if (question.equals("multi-tools") && request.redactedConversationMessages().size() == 1) {
                    return Flux.just(
                            new ModelEvent.ToolRequested("one", "project.getOverview",
                                    "{\"projectId\":\"project-1\"}"),
                            new ModelEvent.ToolRequested("two", "project.getOverview",
                                    "{\"projectId\":\"project-1\"}"),
                            new ModelEvent.Completed("", 7, 8));
                }
                if (question.equals("multi-tools")) {
                    typedToolResult.set(request.redactedConversationMessages().getLast()
                            instanceof ModelRequest.ToolResultMessage);
                    return Flux.just(new ModelEvent.TextDelta("answer"), new ModelEvent.Completed("answer", 3, 4));
                }
                if (question.equals("oversized-completed")) {
                    return Flux.just(new ModelEvent.Completed("x".repeat(64 * 1024 + 1), 1, 1));
                }
                if (question.contains("project-1") && request.redactedConversationMessages().size() == 1
                        && request.allowedToolSpecifications().stream()
                                .anyMatch(tool -> tool.key().equals("project.getOverview"))) {
                    return Flux.just(new ModelEvent.ToolRequested("tool-1", "project.getOverview",
                            "{\"projectId\":\"project-1\"}"));
                }
                return Flux.just(new ModelEvent.TextDelta("answer"), new ModelEvent.Completed("answer", 2, 3));
            }

            int subscriptions() { return subscriptions.get(); }
            boolean disposed() { return disposed.get(); }
            boolean completedEmitted() { return completedEmitted.get(); }
            boolean sawTypedToolResult() { return typedToolResult.get(); }
            int modelCalls() { return modelCalls.get(); }
            void reset() {
                subscriptions.set(0); disposed.set(false); completedEmitted.set(false); typedToolResult.set(false);
                modelCalls.set(0);
            }
        }

        @Bean
        KnowledgeSearchService knowledgeSearchService() {
            KnowledgeSearchService service = mock(KnowledgeSearchService.class);
            when(service.search(any(), any())).thenAnswer(invocation -> {
                com.smart.agent.knowledge.KnowledgeSearchQuery query = invocation.getArgument(0);
                if (query.query().contains("slow-budget")) {
                    try { Thread.sleep(500); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return List.of();
                    }
                }
                int count = query.query().contains("many citation") ? 25 : 1;
                return IntStream.range(0, count).mapToObj(index -> new KnowledgeCitation(
                        "doc-" + index, "version-1", "工程规范", 2, "安全", "sensitive excerpt", 0.9,
                        "citation-token-" + index)).toList();
            });
            return service;
        }

        @Bean
        ChatController chatController(ChatOrchestrator orchestrator) {
            return new ChatController(orchestrator);
        }
    }

    static final class InMemoryConversationRepository implements ConversationRepository {
        private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();

        @Override
        public Conversation save(Conversation conversation) {
            conversations.put(conversation.id(), conversation);
            return conversation;
        }

        @Override
        public Optional<Conversation> findByIdAndTenantIdAndUserId(String tenantId, String userId, String id) {
            return Optional.ofNullable(conversations.get(id))
                    .filter(conversation -> conversation.tenantId().equals(tenantId))
                    .filter(conversation -> conversation.userId().equals(userId));
        }
    }

    static final class InMemoryAgentRunRepository implements AgentRunRepository {
        private final Map<String, AgentRun> runs = new ConcurrentHashMap<>();

        @Override
        public AgentRun save(AgentRun run) {
            runs.put(run.id(), run);
            return run;
        }

        @Override
        public Optional<AgentRun> findByIdAndTenantIdAndUserId(String tenantId, String userId, String id) {
            return Optional.ofNullable(runs.get(id))
                    .filter(run -> run.tenantId().equals(tenantId))
                    .filter(run -> run.userId().equals(userId));
        }

        @Override
        public List<AgentRun> findByTenantIdAndUserIdAndConversationId(
                String tenantId, String userId, String conversationId) {
            return runs.values().stream()
                    .filter(run -> run.tenantId().equals(tenantId))
                    .filter(run -> run.userId().equals(userId))
                    .filter(run -> run.conversationId().equals(conversationId))
                    .toList();
        }

        void clear() {
            runs.clear();
        }
    }

    static final class InMemoryAgentRunStepRepository implements AgentRunStepRepository {
        private final List<AgentRunStep> steps = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final AtomicReference<Duration> nextDelay = new AtomicReference<>();

        @Override
        public AgentRunStep save(AgentRunStep step) {
            Duration delay = nextDelay.getAndSet(null);
            if (delay != null) {
                try { Thread.sleep(delay); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
            steps.add(step);
            return step;
        }

        void delayNextSave(Duration delay) { nextDelay.set(delay); }

        @Override
        public List<AgentRunStep> findByTenantIdAndUserIdAndRunIdOrderBySequence(
                String tenantId, String userId, String runId) {
            return steps.stream().filter(step -> step.runId().equals(runId))
                    .sorted(java.util.Comparator.comparingLong(AgentRunStep::sequence)).toList();
        }
    }
}
