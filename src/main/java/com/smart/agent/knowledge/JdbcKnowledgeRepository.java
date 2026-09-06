package com.smart.agent.knowledge;

import java.util.List;
import java.util.Collection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JdbcKnowledgeRepository implements KnowledgeRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcKnowledgeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void save(KnowledgeDocument document) {
        upsertSpace(document);
        upsertDocument(document);
        upsertVersion(document);
        replaceChunks(document);
        upsertProjectAcl(document);
        upsertIngestionJob(document);
    }

    @Override
    @Transactional
    public void saveManagedDocument(KnowledgeDocument document) {
        int changed = jdbcTemplate.update("UPDATE ai_document_version SET attachment_id = ?, source_text = ?, "
                        + "source_checksum = ?, parser_version = ?, embedding_model_key = ?, status = 'DRAFT', "
                        + "failure_code = NULL, updated_by = ?, update_time = CURRENT_TIMESTAMP(3), "
                        + "deleted = b'0', version = version + 1 WHERE id = ? AND document_id = ? AND tenant_id = ?",
                document.attachmentId(), document.sourceText(), document.sourceChecksum(), document.parserVersion(),
                document.embeddingModelKey(), document.actorId(), document.versionId(), document.id(), document.tenantId());
        if (changed == 0) {
            throw new IllegalStateException("Managed document version does not exist");
        }
        replaceChunks(document);
        upsertIngestionJob(document);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markIndexingSucceeded(String documentId) {
        jdbcTemplate.update("UPDATE ai_ingestion_job SET status = 'indexed', failure_code = NULL, "
                        + "finished_time = CURRENT_TIMESTAMP(3), update_time = CURRENT_TIMESTAMP(3), version = version + 1 "
                        + "WHERE document_id = ? AND deleted = b'0'",
                documentId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markIndexingFailed(String documentId, String failureCode) {
        jdbcTemplate.update("UPDATE ai_ingestion_job SET status = 'failed', failure_code = ?, "
                        + "finished_time = CURRENT_TIMESTAMP(3), update_time = CURRENT_TIMESTAMP(3), version = version + 1 "
                        + "WHERE document_id = ? AND deleted = b'0'",
                failureCode, documentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeChunkMetadata> findPublishedChunks(String tenantId, Collection<String> chunkIds) {
        if (tenantId == null || tenantId.isBlank() || chunkIds == null || chunkIds.isEmpty()) {
            return List.of();
        }
        List<String> ids = chunkIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(ids.size(), "?"));
        List<Object> parameters = new java.util.ArrayList<>();
        parameters.add(tenantId);
        parameters.addAll(ids);
        return jdbcTemplate.query("SELECT c.id, c.document_id, c.document_version_id, c.tenant_id, d.space_id, "
                        + "d.project_id, d.title, c.page_number, c.section_title, c.content "
                        + "FROM ai_document_chunk c "
                        + "JOIN ai_document d ON d.id = c.document_id AND d.tenant_id = c.tenant_id "
                        + "JOIN ai_document_version v ON v.id = c.document_version_id "
                        + "AND v.document_id = c.document_id AND v.tenant_id = c.tenant_id "
                        + "JOIN ai_knowledge_space s ON s.id = d.space_id AND s.tenant_id = d.tenant_id "
                        + "WHERE c.tenant_id = ? AND c.id IN (" + placeholders + ") "
                        + "AND c.deleted = b'0' AND c.status = 'published' "
                        + "AND d.deleted = b'0' AND d.status = 'published' "
                        + "AND v.deleted = b'0' AND v.status = 'published' "
                        + "AND s.deleted = b'0' AND s.status = 'active'",
                (resultSet, rowNumber) -> new KnowledgeChunkMetadata(
                        resultSet.getString("id"), resultSet.getString("document_id"),
                        resultSet.getString("document_version_id"), resultSet.getString("tenant_id"),
                        resultSet.getString("space_id"), resultSet.getString("project_id"), resultSet.getString("title"),
                        resultSet.getObject("page_number", Integer.class), resultSet.getString("section_title"),
                        resultSet.getString("content"), true, false),
                parameters.toArray());
    }

    private void upsertSpace(KnowledgeDocument document) {
        jdbcTemplate.update("INSERT INTO ai_knowledge_space "
                        + "(id, tenant_id, organization_id, name, status, created_by, create_time, updated_by, update_time) "
                        + "VALUES (?, ?, ?, ?, 'active', ?, CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3)) "
                        + "ON DUPLICATE KEY UPDATE organization_id = VALUES(organization_id), name = VALUES(name), "
                        + "updated_by = VALUES(updated_by), update_time = CURRENT_TIMESTAMP(3), deleted = b'0', version = version + 1",
                document.spaceId(), document.tenantId(), document.organizationId(), document.spaceId(),
                document.actorId(), document.actorId());
    }

    private void upsertDocument(KnowledgeDocument document) {
        jdbcTemplate.update("INSERT INTO ai_document "
                        + "(id, tenant_id, space_id, organization_id, project_id, title, source_type, status, "
                        + "created_by, create_time, updated_by, update_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'text', ?, ?, CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3)) "
                        + "ON DUPLICATE KEY UPDATE title = VALUES(title), status = VALUES(status), "
                        + "updated_by = VALUES(updated_by), update_time = CURRENT_TIMESTAMP(3), deleted = b'0', version = version + 1",
                document.id(), document.tenantId(), document.spaceId(), document.organizationId(), document.projectId(),
                document.title(), document.status(), document.actorId(), document.actorId());
    }

    private void upsertVersion(KnowledgeDocument document) {
        jdbcTemplate.update("INSERT INTO ai_document_version "
                        + "(id, document_id, tenant_id, version_no, source_text, source_checksum, parser_version, "
                        + "embedding_model_key, status, created_by, create_time, updated_by, update_time) "
                        + "VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3)) "
                        + "ON DUPLICATE KEY UPDATE status = VALUES(status), updated_by = VALUES(updated_by), "
                        + "update_time = CURRENT_TIMESTAMP(3), deleted = b'0', version = version + 1",
                document.versionId(), document.id(), document.tenantId(), document.sourceText(),
                document.sourceChecksum(), document.parserVersion(), document.embeddingModelKey(), document.status(),
                document.actorId(), document.actorId());
    }

    private void replaceChunks(KnowledgeDocument document) {
        jdbcTemplate.update("DELETE FROM ai_document_chunk WHERE document_version_id = ?", document.versionId());
        List<Object[]> arguments = document.chunks().stream().map(chunk -> new Object[] {
            chunk.id(), chunk.documentId(), chunk.documentVersionId(), chunk.tenantId(), chunk.ordinal(), chunk.content(),
            chunk.checksum(), chunk.vectorPointId(), chunk.pageNumber(), chunk.sectionTitle(), document.status(),
            document.actorId(), document.actorId()
        }).toList();
        jdbcTemplate.batchUpdate("INSERT INTO ai_document_chunk "
                + "(id, document_id, document_version_id, tenant_id, ordinal_no, content, content_checksum, "
                + "vector_point_id, page_number, section_title, status, created_by, create_time, updated_by, update_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3))", arguments);
    }

    private void upsertProjectAcl(KnowledgeDocument document) {
        if (document.projectId() == null) {
            return;
        }
        String aclId = java.util.UUID.nameUUIDFromBytes(
                (document.id() + "|project|" + document.projectId()).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
        jdbcTemplate.update("INSERT INTO ai_document_acl "
                        + "(id, document_id, tenant_id, subject_type, subject_id, permission, created_by, create_time, "
                        + "updated_by, update_time) VALUES (?, ?, ?, 'project', ?, 'read', ?, CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3)) "
                        + "ON DUPLICATE KEY UPDATE updated_by = VALUES(updated_by), update_time = CURRENT_TIMESTAMP(3), "
                        + "deleted = b'0', version = version + 1",
                aclId, document.id(), document.tenantId(), document.projectId(), document.actorId(), document.actorId());
    }

    private void upsertIngestionJob(KnowledgeDocument document) {
        jdbcTemplate.update("INSERT INTO ai_ingestion_job "
                        + "(id, document_id, document_version_id, tenant_id, status, attempt_count, started_time, "
                        + "created_by, create_time, updated_by, update_time) "
                        + "VALUES (?, ?, ?, ?, 'pending', 1, CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3), ?, CURRENT_TIMESTAMP(3)) "
                        + "ON DUPLICATE KEY UPDATE status = 'pending', attempt_count = attempt_count + 1, failure_code = NULL, "
                        + "started_time = CURRENT_TIMESTAMP(3), finished_time = NULL, updated_by = VALUES(updated_by), "
                        + "update_time = CURRENT_TIMESTAMP(3), deleted = b'0', version = version + 1",
                document.versionId(), document.id(), document.versionId(), document.tenantId(),
                document.actorId(), document.actorId());
    }
}
