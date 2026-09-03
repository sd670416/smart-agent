package com.smart.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.conversation.Conversation;
import com.smart.agent.conversation.ConversationService;
import com.smart.agent.knowledge.IngestTextCommand;
import com.smart.agent.knowledge.KnowledgeIngestionService;
import com.smart.agent.run.AgentRun;
import com.smart.agent.run.AgentRunRepository;
import com.smart.agent.run.AgentRunStatus;
import com.smart.agent.run.AgentRunStep;
import com.smart.agent.run.AgentRunStepRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("acceptance")
@EnabledIfSystemProperty(named = "agent.it.acceptance", matches = "true")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "AGENT_LOCAL_CONTEXT_SECRET=acceptance-test-context-secret",
        "agent.persistence.enabled=true",
        "agent.qdrant.enabled=true",
        "agent.embedding.mode=local-hash",
        "agent.model.mode=local"
})
class AcceptanceIT {
    private static final String CONTEXT_SECRET = "acceptance-test-context-secret";
    private static final String COLLECTION = "agent_acceptance_" + UUID.randomUUID().toString().replace("-", "");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6")
            .withDatabaseName("smart_agent")
            .withUsername("smart_agent")
            .withPassword("smart_agent_dev");

    @Container
    static final GenericContainer<?> QDRANT = new GenericContainer<>("qdrant/qdrant:v1.18.2")
            .withExposedPorts(6334);

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private KnowledgeIngestionService ingestionService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private AgentRunRepository runRepository;

    @Autowired
    private AgentRunStepRepository stepRepository;

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("agent.qdrant.host", QDRANT::getHost);
        registry.add("agent.qdrant.grpc-port", () -> QDRANT.getMappedPort(6334));
        registry.add("agent.qdrant.collection-name", () -> COLLECTION);
        registry.add("agent.qdrant.vector-dimension", () -> 64);
    }

    @Test
    void completesProjectAndKnowledgeQuestionWithTenantScopedCitationAndStructuredAudit() {
        String tenantOneDocumentId = ingestionService.ingestText(new IngestTextCommand(
                "tenant-1", "space-1", "org-1", "project-1", "项目安全检查制度",
                "项目安全检查制度\n项目现场必须执行每日安全检查，并保留检查记录。",
                "published", "user-1"));
        ingestionService.ingestText(new IngestTextCommand(
                "tenant-2", "space-1", "org-2", "project-1", "租户二安全制度",
                "租户二项目现场必须执行独立的安全检查制度。", "published", "user-2"));

        Conversation tenantOneConversation = conversationService.create("tenant-1", "user-1", "验收问答");
        List<String> tenantOneEvents = stream(tenantOneConversation.id(), signedToken(
                "tenant-1", "user-1", Set.of("project:read", "knowledge:read"), Set.of("project-1"), Set.of("space-1")));

        assertThat(tenantOneEvents).anyMatch(event -> event.contains("\"type\":\"tool_start\"")
                && event.contains("project.getOverview"));
        assertThat(tenantOneEvents).anyMatch(event -> event.contains("\"type\":\"citation\"")
                && event.contains("项目安全检查制度") && event.contains(tenantOneDocumentId));
        assertThat(tenantOneEvents).anyMatch(event -> event.contains("\"type\":\"message_delta\"")
                && event.contains("项目 project-1 当前状态为 IN_PROGRESS，完成进度为 42%"));
        assertThat(tenantOneEvents.getLast()).contains("\"type\":\"message_end\"");

        AgentRun tenantOneRun = onlyRun("tenant-1", "user-1", tenantOneConversation.id());
        assertThat(tenantOneRun.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(tenantOneRun.toolExecutionSummaries()).contains("project.getOverview").doesNotContain("projectName");
        assertThat(tenantOneRun.citationSummaries()).isNotBlank().doesNotContain("每日安全检查");
        assertThat(tenantOneRun.inputTokens()).isPositive();
        assertThat(tenantOneRun.outputTokens()).isPositive();
        assertThat(stepRepository.findByTenantIdAndUserIdAndRunIdOrderBySequence(
                "tenant-1", "user-1", tenantOneRun.id()))
                .extracting(AgentRunStep::type).contains("KNOWLEDGE_SEARCH", "TOOL", "MODEL", "TERMINAL");
        assertThat(stepRepository.findByTenantIdAndUserIdAndRunIdOrderBySequence(
                "tenant-1", "user-1", tenantOneRun.id()))
                .filteredOn(step -> step.type().equals("TERMINAL"))
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.status()).isEqualTo("COMPLETED");
                    assertThat(step.safeOutputSummary()).contains("COMPLETED");
                });

        Conversation tenantTwoConversation = conversationService.create("tenant-2", "user-2", "隔离验证");
        List<String> tenantTwoEvents = stream(tenantTwoConversation.id(), signedToken(
                "tenant-2", "user-2", Set.of("project:read", "knowledge:read"), Set.of("project-1"), Set.of("space-1")));

        assertThat(tenantTwoEvents).anyMatch(event -> event.contains("租户二安全制度"));
        assertThat(tenantTwoEvents).noneMatch(event -> event.contains(tenantOneDocumentId)
                || event.contains("项目安全检查制度"));
        assertThat(onlyRun("tenant-2", "user-2", tenantTwoConversation.id()).status())
                .isEqualTo(AgentRunStatus.COMPLETED);
    }

    private List<String> stream(String conversationId, String contextToken) {
        return webTestClient.post().uri("/agent/chat/stream")
                .header("X-Agent-Context", contextToken)
                .bodyValue(Map.of(
                        "conversationId", conversationId,
                        "question", "查询项目 project-1 概况和适用安全规范",
                        "pageContext", Map.of("projectId", "project-1")))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class)
                .getResponseBody()
                .collectList()
                .block(Duration.ofSeconds(15));
    }

    private AgentRun onlyRun(String tenantId, String userId, String conversationId) {
        return runRepository.findByTenantIdAndUserIdAndConversationId(tenantId, userId, conversationId)
                .stream().findFirst().orElseThrow();
    }

    private static String signedToken(
            String tenantId, String userId, Set<String> permissions, Set<String> projectIds, Set<String> spaceIds) {
        try {
            byte[] payload = new ObjectMapper().writeValueAsBytes(Map.of(
                    "tenantId", tenantId,
                    "userId", userId,
                    "identityId", "identity-" + tenantId,
                    "permissions", permissions,
                    "projectIds", projectIds,
                    "knowledgeSpaceIds", spaceIds,
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
}
