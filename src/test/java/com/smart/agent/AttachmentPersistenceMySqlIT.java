package com.smart.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.agent.attachment.Attachment;
import com.smart.agent.attachment.AttachmentPurpose;
import com.smart.agent.attachment.AttachmentRepository;
import com.smart.agent.attachment.AttachmentService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
        "AGENT_LOCAL_CONTEXT_SECRET=attachment-mysql-test-context-secret"
})
class AttachmentPersistenceMySqlIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.6")
            .withDatabaseName("smart_agent")
            .withUsername("smart_agent")
            .withPassword("smart_agent_dev");

    @Autowired
    private AttachmentService service;

    @Autowired
    private AttachmentRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    @Transactional
    void migrationPersistsAttachmentsAndEnforcesTenantScopedLookup() {
        Attachment attachment = service.registerUpload("tenant-1", "user-1", AttachmentPurpose.CHAT_ATTACHMENT,
                "drawing.dwg", Instant.now().plusSeconds(300));

        assertThat(repository.findByIdAndTenantIdAndUserId(attachment.id(), "tenant-1", "user-1")).isPresent();
        assertThat(repository.findByIdAndTenantIdAndUserId(attachment.id(), "tenant-2", "user-1")).isEmpty();
        assertThat(repository.findByIdAndTenantIdAndUserId(attachment.id(), "tenant-1", "user-2")).isEmpty();
        assertThat(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class)).contains("ai_attachment", "ai_knowledge_space", "ai_document",
                "ai_document_version", "ai_role_knowledge_grant", "ai_citation", "ai_audit_log");
        assertThat(jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'ai_attachment'",
                String.class)).contains("cleanup_completed_at");
        assertThat(jdbcTemplate.queryForList(
                "SELECT index_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'ai_attachment'",
                String.class)).contains("idx_ai_attachment_cleanup");
    }

    @Test
    void migrationEnforcesTenantObjectKeyUniqueness() {
        String key = "ai/tenant-1/chat-attachment/2026/09/04/user-1/" + UUID.randomUUID() + ".txt";
        insertAttachment(UUID.randomUUID(), "tenant-1", key);

        assertThatThrownBy(() -> insertAttachment(UUID.randomUUID(), "tenant-1", key))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertAttachment(UUID.randomUUID(), "tenant-2", key);
    }

    private void insertAttachment(UUID id, String tenantId, String objectKey) {
        jdbcTemplate.update("INSERT INTO ai_attachment "
                        + "(id, tenant_id, user_id, purpose, original_filename, object_key, status, "
                        + "upload_expires_at, create_time, update_time, version) "
                        + "VALUES (?, ?, 'user-1', 'CHAT_ATTACHMENT', 'file.txt', ?, 'PENDING_UPLOAD', "
                        + "CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)",
                id.toString(), tenantId, objectKey);
    }
}
