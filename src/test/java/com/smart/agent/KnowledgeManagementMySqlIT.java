package com.smart.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.agent.knowledge.manage.JdbcKnowledgeManagementRepository;
import com.smart.agent.knowledge.manage.KnowledgeDocumentVersion;
import com.smart.agent.knowledge.manage.KnowledgeScope;
import com.smart.agent.knowledge.manage.KnowledgeSpace;
import com.smart.agent.knowledge.manage.KnowledgeStatus;
import com.smart.agent.knowledge.manage.ManagedKnowledgeDocument;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("mysql")
@EnabledIfSystemProperty(named = "agent.it.mysql", matches = "true")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "agent.persistence.enabled=true",
        "agent.qdrant.enabled=false",
        "AGENT_LOCAL_CONTEXT_SECRET=knowledge-mysql-test-context-secret"
})
class KnowledgeManagementMySqlIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6")
            .withDatabaseName("smart_agent").withUsername("smart_agent").withPassword("smart_agent_dev");

    @Autowired private JdbcKnowledgeManagementRepository repository;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    @Transactional
    void persistsScopedSpacesAndKeepsTenantPaginationIsolated() {
        Instant now = Instant.parse("2026-09-04T10:00:00Z");
        KnowledgeSpace saved = repository.saveSpace(new KnowledgeSpace(UUID.randomUUID(), "tenant-1", "项目资料",
                "施工资料", KnowledgeScope.PROJECT, "project-1", KnowledgeStatus.DRAFT,
                "user-1", now, "user-1", now));

        assertThat(repository.findSpace(saved.id(), "tenant-1")).contains(saved);
        assertThat(repository.findSpace(saved.id(), "tenant-2")).isEmpty();
        assertThat(repository.listSpaces("tenant-1", java.util.Set.of("project-1"), 0, 20).items()).containsExactly(saved);
        assertThat(repository.listSpaces("tenant-2", java.util.Set.of(), 0, 20).items()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT scope FROM ai_knowledge_space WHERE id = ?", String.class,
                saved.id().toString())).isEqualTo("PROJECT");
    }

    @Test
    @Transactional
    void publishingNewDraftDisablesOldVersionAndUpdatesActivePointerAtomically() {
        Instant now = Instant.parse("2026-09-04T10:00:00Z");
        KnowledgeSpace space = repository.saveSpace(new KnowledgeSpace(UUID.randomUUID(), "tenant-1", "规范",
                null, KnowledgeScope.TENANT, null, KnowledgeStatus.PUBLISHED,
                "user-1", now, "user-1", now));
        ManagedKnowledgeDocument document = repository.saveDocument(new ManagedKnowledgeDocument(UUID.randomUUID(),
                "tenant-1", space.id(), "规范.pdf", KnowledgeStatus.DRAFT, null,
                "user-1", now, "user-1", now));
        KnowledgeDocumentVersion first = repository.saveVersion(version(document.id(), 1, now));
        repository.publish(first.id(), "tenant-1", "user-1", now.plusSeconds(1));
        KnowledgeDocumentVersion second = repository.saveVersion(version(document.id(), 2, now.plusSeconds(2)));

        repository.publish(second.id(), "tenant-1", "user-1", now.plusSeconds(3));

        assertThat(repository.findVersion(first.id(), "tenant-1").orElseThrow().status())
                .isEqualTo(KnowledgeStatus.DISABLED);
        assertThat(repository.findVersion(second.id(), "tenant-1").orElseThrow().status())
                .isEqualTo(KnowledgeStatus.PUBLISHED);
        assertThat(repository.findActiveVersion(document.id(), "tenant-1").orElseThrow().id()).isEqualTo(second.id());
    }

    private KnowledgeDocumentVersion version(UUID documentId, int number, Instant now) {
        return new KnowledgeDocumentVersion(UUID.randomUUID(), documentId, UUID.randomUUID(), "tenant-1", number,
                KnowledgeStatus.DRAFT, null, "user-1", now, "user-1", now);
    }
}
