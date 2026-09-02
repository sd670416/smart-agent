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
import org.springframework.http.MediaType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

    private String conversationId;

    @BeforeEach
    void createConversation() {
        runRepository.clear();
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
        AgentRun run = runRepository.findByConversationId(conversationId).getFirst();
        assertThat(run.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(run.traceId()).isNotBlank();
        assertThat(run.toolExecutionSummaries()).contains("project.getOverview").doesNotContain("projectName");
        assertThat(run.inputTokens()).isZero();
        assertThat(run.outputTokens()).isZero();
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
        assertThat(runRepository.findByConversationId(conversationId)).isEmpty();
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
        AgentRun run = runRepository.findByConversationId(conversationId).getFirst();
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

        assertThat(indexOf(events, "citation")).isGreaterThanOrEqualTo(0)
                .isLessThan(indexOf(events, "message_delta"));
        AgentRun run = runRepository.findByConversationId(conversationId).getFirst();
        assertThat(run.citationSummaries()).contains("citation-token").doesNotContain("sensitive excerpt");
    }

    @Test
    void malformedToolInputEndsWithSafeFailure() {
        List<String> events = stream("malformed-tool", Set.of("project:read"), Set.of("project-1"));
        assertThat(events.getLast()).contains("AGENT_TOOL_INVALID_INPUT").doesNotContain("not-json");
        assertThat(runRepository.findByConversationId(conversationId).getFirst().status())
                .isEqualTo(AgentRunStatus.FAILED);
    }

    @Test
    void modelTimeoutIsPersistedAndStreamedSafely() {
        List<String> events = stream("model-timeout", Set.of(), Set.of());
        assertThat(events.getLast()).contains("MODEL_TIMEOUT").doesNotContain("provider secret");
        assertThat(runRepository.findByConversationId(conversationId).getFirst().status())
                .isEqualTo(AgentRunStatus.TIMEOUT);
    }

    @Test
    void enforcesFiveToolCallLimit() {
        List<String> events = stream("tool-limit", Set.of("project:read"), Set.of("project-1"));
        assertThat(events.getLast()).contains("AGENT_TOOL_CALL_LIMIT");
        AgentRun run = runRepository.findByConversationId(conversationId).getFirst();
        assertThat(run.toolExecutionSummaries().lines()).hasSize(5);
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
            List<AgentRun> runs = runRepository.findByConversationId(conversationId);
            if (!runs.isEmpty() && runs.getFirst().status() == expected) return;
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        assertThat(runRepository.findByConversationId(conversationId).getFirst().status()).isEqualTo(expected);
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
        AgentRunService agentRunService(
                InMemoryAgentRunRepository runs, InMemoryConversationRepository conversations) {
            return new AgentRunService(runs, conversations);
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
        ModelGateway scenarioModelGateway() {
            return request -> {
                String question = request.redactedConversationMessages().getFirst().content();
                if (question.equals("malformed-tool")) {
                    return Flux.just(new ModelEvent.ToolRequested("bad", "project.getOverview", "not-json"));
                }
                if (question.equals("model-timeout")) {
                    return Flux.just(new ModelEvent.Failed("MODEL_TIMEOUT", "provider secret"));
                }
                if (question.equals("never-completes")) return Flux.never();
                if (question.equals("tool-limit")) {
                    return Flux.just(new ModelEvent.ToolRequested("repeat", "project.getOverview",
                            "{\"projectId\":\"project-1\"}"));
                }
                if (question.contains("project-1") && request.redactedConversationMessages().size() == 1) {
                    return Flux.just(new ModelEvent.ToolRequested("tool-1", "project.getOverview",
                            "{\"projectId\":\"project-1\"}"));
                }
                return Flux.just(new ModelEvent.TextDelta("answer"), new ModelEvent.Completed("answer", 0, 0));
            };
        }

        @Bean
        KnowledgeSearchService knowledgeSearchService() {
            KnowledgeSearchService service = mock(KnowledgeSearchService.class);
            when(service.search(any(), any())).thenAnswer(invocation -> {
                com.smart.agent.knowledge.KnowledgeSearchQuery query = invocation.getArgument(0);
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
        public List<AgentRun> findByConversationId(String conversationId) {
            return runs.values().stream()
                    .filter(run -> run.conversationId().equals(conversationId))
                    .toList();
        }

        void clear() {
            runs.clear();
        }
    }
}
