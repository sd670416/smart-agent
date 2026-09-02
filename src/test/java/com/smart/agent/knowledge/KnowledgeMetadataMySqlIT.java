package com.smart.agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("mysql")
@EnabledIfSystemProperty(named = "agent.it.mysql", matches = "true")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"agent.persistence.enabled=true", "agent.qdrant.enabled=false"})
class KnowledgeMetadataMySqlIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6")
            .withDatabaseName("smart_agent")
            .withUsername("smart_agent")
            .withPassword("smart_agent_dev");

    @Autowired
    private KnowledgeRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    void migrationCreatesCompleteSchemaAndRepositoryPersistsRebuildableMetadata() {
        KnowledgeChunk chunk = new KnowledgeChunk(
                "chunk-1", "doc-1", "version-1", "tenant-1", 0, "normalized source", "chunk-checksum",
                "point-1", 7, "Safety");
        KnowledgeDocument document = new KnowledgeDocument(
                "doc-1", "version-1", "tenant-1", "space-1", "org-1", "project-1", "Safety guide",
                "published", "normalized source", "source-checksum", "plain-text-v1", "local-hash-v1",
                "user-1", List.of(chunk));

        repository.save(document);

        assertThat(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() "
                        + "AND table_name LIKE 'ai_%'", String.class))
                .contains("ai_knowledge_space", "ai_document", "ai_document_version", "ai_document_acl",
                        "ai_document_chunk", "ai_ingestion_job");
        assertThat(jdbcTemplate.queryForMap(
                "SELECT tenant_id, organization_id, project_id, status FROM ai_document WHERE id = ?", "doc-1"))
                .containsEntry("tenant_id", "tenant-1")
                .containsEntry("organization_id", "org-1")
                .containsEntry("project_id", "project-1")
                .containsEntry("status", "published");
        assertThat(jdbcTemplate.queryForMap(
                "SELECT source_checksum, parser_version, embedding_model_key, source_text "
                        + "FROM ai_document_version WHERE id = ?", "version-1"))
                .containsEntry("source_checksum", "source-checksum")
                .containsEntry("parser_version", "plain-text-v1")
                .containsEntry("embedding_model_key", "local-hash-v1")
                .containsEntry("source_text", "normalized source");
        assertThat(jdbcTemplate.queryForMap(
                "SELECT vector_point_id, page_number, section_title FROM ai_document_chunk WHERE id = ?", "chunk-1"))
                .containsEntry("vector_point_id", "point-1")
                .containsEntry("page_number", 7)
                .containsEntry("section_title", "Safety");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ai_ingestion_job WHERE document_version_id = ?", String.class, "version-1"))
                .isEqualTo("pending");
    }
}
