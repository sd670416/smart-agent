package com.smart.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.agent.conversation.Conversation;
import com.smart.agent.conversation.ConversationService;
import com.smart.agent.conversation.Message;
import com.smart.agent.run.AgentRun;
import com.smart.agent.run.AgentRunService;
import com.smart.agent.run.AgentRunStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("mysql")
@EnabledIfSystemProperty(named = "agent.it.mysql", matches = "true")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "agent.persistence.enabled=true")
class PersistenceMySqlIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6")
            .withDatabaseName("smart_agent")
            .withUsername("smart_agent")
            .withPassword("smart_agent_dev");

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private AgentRunService agentRunService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired private com.smart.agent.question.QuestionRecordService questionRecords;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void persistsIdempotentSourcesAndExcludesFutureContextAndModelPayloads() {
        var c = conversationService.create("question-tenant", "question-user", "question");
        var user = conversationService.appendMessage("question-tenant", "question-user", c.id(), Message.Role.USER, "password=secret question");
        var run = agentRunService.start("question-tenant", "question-user", c.id());
        agentRunService.bindMessages("question-tenant", "question-user", run.id(), user.id(), null);
        agentRunService.finishTerminal("question-tenant", "question-user", run.id(), AgentRunStatus.RECEIVED, AgentRunStatus.FAILED, "AGENT_MODEL_FAILED");
        var answer = conversationService.appendMessage("question-tenant", "question-user", c.id(), Message.Role.ASSISTANT, "failure");
        agentRunService.bindMessages("question-tenant", "question-user", run.id(), null, answer.id());
        conversationService.appendMessage("question-tenant", "question-user", c.id(), Message.Role.USER, "future message");
        agentRunService.recordStep("question-tenant", "question-user", run.id(), "AI_DEBUG_MODEL_REQUEST", "secret", "raw payload");
        var context = new com.smart.agent.security.AgentUserContext("question-tenant", "question-user", "i", java.util.Set.of("aiQuestion:menu"), java.util.Set.of());
        questionRecords.collectSafely(context, run.id(), "SYSTEM", "PROJECT", "failure");
        var detail = questionRecords.feedback(context, c.id(), run.id(), answer.id());
        questionRecords.feedback(context, c.id(), run.id(), answer.id());
        assertThat(detail.get("sources")).isEqualTo(java.util.List.of("SYSTEM", "USER"));
        assertThat(detail.toString()).doesNotContain("secret", "future message", "raw payload");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_question_record WHERE tenant_id=? AND run_id=?", Long.class, "question-tenant", run.id())).isEqualTo(1L);
        var other = new com.smart.agent.security.AgentUserContext("other-tenant", "question-user", "i", java.util.Set.of("aiQuestion:menu"), java.util.Set.of());
        assertThatThrownBy(() -> questionRecords.detail(other, (String) detail.get("id"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    void appliesFlywayMigrationPersistsOrderedMessagesAndDetectsOptimisticConflicts() {
        Conversation conversation = conversationService.create("tenant-1", "user-1", "项目问答");
        conversationService.appendMessage("tenant-1", "user-1", conversation.id(), Message.Role.USER, "项目进度如何");
        conversationService.appendMessage("tenant-1", "user-1", conversation.id(), Message.Role.ASSISTANT, "正在查询");

        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            Conversation reloaded = entityManager.find(Conversation.class, conversation.id());
            assertThat(reloaded.messages()).extracting(Message::sequence).containsExactly(1L, 2L);
            assertThat(entityManager.createNativeQuery("SHOW TABLES LIKE 'ai_run_step'").getResultList()).isNotEmpty();
        }

        AgentRun run = agentRunService.start("tenant-1", "user-1", conversation.id());
        assertOptimisticTransitionConflict(run.id());
    }

    private void assertOptimisticTransitionConflict(String runId) {
        EntityManager first = entityManagerFactory.createEntityManager();
        EntityManager second = entityManagerFactory.createEntityManager();
        try {
            first.getTransaction().begin();
            second.getTransaction().begin();
            AgentRun firstRun = first.find(AgentRun.class, runId);
            AgentRun secondRun = second.find(AgentRun.class, runId);

            firstRun.transition(AgentRunStatus.ROUTING);
            first.getTransaction().commit();
            secondRun.transition(AgentRunStatus.ROUTING);

            assertThatThrownBy(second.getTransaction()::commit)
                    .isInstanceOfAny(OptimisticLockException.class, RollbackException.class);
        } finally {
            if (first.getTransaction().isActive()) {
                first.getTransaction().rollback();
            }
            if (second.getTransaction().isActive()) {
                second.getTransaction().rollback();
            }
            first.close();
            second.close();
        }
    }
}
