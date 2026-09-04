package com.smart.agent.knowledge.manage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JdbcKnowledgeManagementRepository implements KnowledgeManagementRepository {
    private final JdbcTemplate jdbc;

    public JdbcKnowledgeManagementRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public KnowledgeSpace saveSpace(KnowledgeSpace value) {
        jdbc.update("INSERT INTO ai_knowledge_space (id, tenant_id, name, description, scope, project_id, status, "
                        + "created_by, create_time, updated_by, update_time, deleted, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, b'0', 0) "
                        + "ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description), status=VALUES(status), "
                        + "updated_by=VALUES(updated_by), update_time=VALUES(update_time), version=version+1",
                text(value.id()), value.tenantId(), value.name(), value.description(), value.scope().name(),
                value.projectId(), value.status().name(), value.createdBy(), timestamp(value.createdAt()),
                value.updatedBy(), timestamp(value.updatedAt()));
        return findSpace(value.id(), value.tenantId()).orElseThrow();
    }

    @Override
    public Optional<KnowledgeSpace> findSpace(UUID id, String tenantId) {
        return one("SELECT * FROM ai_knowledge_space WHERE id=? AND tenant_id=? AND deleted=b'0'", this::space,
                text(id), tenantId);
    }

    @Override
    public void lockSpace(UUID spaceId, String tenantId) {
        if (jdbc.queryForList("SELECT id FROM ai_knowledge_space WHERE id=? AND tenant_id=? AND deleted=b'0' FOR UPDATE",
                String.class, text(spaceId), tenantId).isEmpty()) throw new KnowledgeNotFoundException();
    }

    @Override
    public void lockAttachment(UUID attachmentId, String tenantId, String userId) {
        if (jdbc.queryForList("SELECT id FROM ai_attachment WHERE id=? AND tenant_id=? AND user_id=? FOR UPDATE",
                String.class, text(attachmentId), tenantId, userId).isEmpty()) throw new KnowledgeNotFoundException();
    }

    @Override
    public PageResult<KnowledgeSpace> listSpaces(String tenantId, java.util.Set<String> projectIds, int page, int size) {
        String in = projectIds.isEmpty() ? "NULL" : String.join(",", java.util.Collections.nCopies(projectIds.size(), "?"));
        String where = "tenant_id=? AND deleted=b'0' AND (scope='TENANT' OR project_id IN (" + in + "))";
        java.util.List<Object> args = new java.util.ArrayList<>(); args.add(tenantId); args.addAll(projectIds);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM ai_knowledge_space WHERE " + where,
                Long.class, args.toArray());
        args.add(size); args.add((long) page * size);
        List<KnowledgeSpace> items = jdbc.query("SELECT * FROM ai_knowledge_space WHERE " + where
                        + " ORDER BY update_time DESC, id LIMIT ? OFFSET ?", this::space, args.toArray());
        return new PageResult<>(items, total, page, size);
    }

    @Override
    public ManagedKnowledgeDocument saveDocument(ManagedKnowledgeDocument value) {
        jdbc.update("INSERT INTO ai_document (id, tenant_id, space_id, title, source_type, status, active_version_id, "
                        + "created_by, create_time, updated_by, update_time, deleted, version) VALUES (?, ?, ?, ?, 'oss', ?, ?, ?, ?, ?, ?, b'0', 0) "
                        + "ON DUPLICATE KEY UPDATE title=VALUES(title), status=VALUES(status), active_version_id=VALUES(active_version_id), "
                        + "updated_by=VALUES(updated_by), update_time=VALUES(update_time), version=version+1",
                text(value.id()), value.tenantId(), text(value.spaceId()), value.title(), value.status().name(),
                text(value.activeVersionId()), value.createdBy(), timestamp(value.createdAt()), value.updatedBy(), timestamp(value.updatedAt()));
        return findDocument(value.id(), value.tenantId()).orElseThrow();
    }

    @Override
    public Optional<ManagedKnowledgeDocument> findDocument(UUID id, String tenantId) {
        return one("SELECT * FROM ai_document WHERE id=? AND tenant_id=? AND deleted=b'0'", this::document,
                text(id), tenantId);
    }

    @Override
    public Optional<ManagedKnowledgeDocument> findDocumentBySpaceAndTitle(UUID spaceId, String title, String tenantId) {
        return one("SELECT * FROM ai_document WHERE space_id=? AND title=? AND tenant_id=? AND deleted=b'0' "
                        + "ORDER BY create_time LIMIT 1", this::document, text(spaceId), title, tenantId);
    }

    @Override
    public PageResult<ManagedKnowledgeDocument> listDocuments(UUID spaceId, String tenantId, int page, int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM ai_document WHERE space_id=? AND tenant_id=? AND deleted=b'0'",
                Long.class, text(spaceId), tenantId);
        List<ManagedKnowledgeDocument> items = jdbc.query("SELECT * FROM ai_document WHERE space_id=? AND tenant_id=? "
                        + "AND deleted=b'0' ORDER BY update_time DESC, id LIMIT ? OFFSET ?", this::document,
                text(spaceId), tenantId, size, page * size);
        return new PageResult<>(items, total, page, size);
    }

    @Override
    public int nextVersionNumber(UUID documentId, String tenantId) {
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0) FROM ai_document_version "
                + "WHERE document_id=? AND tenant_id=? AND deleted=b'0'", Integer.class, text(documentId), tenantId);
        return max + 1;
    }

    @Override
    public void lockDocument(UUID documentId, String tenantId) {
        if (jdbc.queryForList("SELECT id FROM ai_document WHERE id=? AND tenant_id=? AND deleted=b'0' FOR UPDATE",
                String.class, text(documentId), tenantId).isEmpty()) throw new KnowledgeNotFoundException();
    }

    @Override
    public KnowledgeDocumentVersion saveVersion(KnowledgeDocumentVersion value) {
        jdbc.update("INSERT INTO ai_document_version (id, document_id, tenant_id, attachment_id, version_no, status, failure_code, "
                        + "created_by, create_time, updated_by, update_time, deleted, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, b'0', 0) "
                        + "ON DUPLICATE KEY UPDATE status=VALUES(status), failure_code=VALUES(failure_code), "
                        + "updated_by=VALUES(updated_by), update_time=VALUES(update_time), version=version+1",
                text(value.id()), text(value.documentId()), value.tenantId(), text(value.attachmentId()), value.versionNo(),
                value.status().name(), value.failureCode(), value.createdBy(), timestamp(value.createdAt()),
                value.updatedBy(), timestamp(value.updatedAt()));
        return findVersion(value.id(), value.tenantId()).orElseThrow();
    }

    @Override
    public Optional<KnowledgeDocumentVersion> findVersion(UUID id, String tenantId) {
        return one("SELECT * FROM ai_document_version WHERE id=? AND tenant_id=? AND deleted=b'0'", this::version,
                text(id), tenantId);
    }

    @Override
    public Optional<KnowledgeDocumentVersion> findVersionByAttachment(UUID attachmentId, String tenantId) {
        return one("SELECT * FROM ai_document_version WHERE attachment_id=? AND tenant_id=? AND deleted=b'0'",
                this::version, text(attachmentId), tenantId);
    }

    @Override
    public Optional<KnowledgeDocumentVersion> findActiveVersion(UUID documentId, String tenantId) {
        return one("SELECT v.* FROM ai_document d JOIN ai_document_version v ON v.id=d.active_version_id "
                        + "AND v.document_id=d.id AND v.tenant_id=d.tenant_id WHERE d.id=? AND d.tenant_id=? "
                        + "AND d.deleted=b'0' AND v.deleted=b'0'", this::version, text(documentId), tenantId);
    }

    @Override
    public Optional<KnowledgeDocumentVersion> findLatestVersion(UUID documentId, String tenantId) {
        return one("SELECT * FROM ai_document_version WHERE document_id=? AND tenant_id=? AND deleted=b'0' "
                + "ORDER BY version_no DESC LIMIT 1", this::version, text(documentId), tenantId);
    }

    @Override
    public KnowledgeDocumentVersion publish(UUID id, String tenantId, String actor, Instant now) {
        KnowledgeDocumentVersion target = findVersion(id, tenantId).orElseThrow(KnowledgeNotFoundException::new);
        lockDocument(target.documentId(), tenantId);
        target = findVersion(id, tenantId).orElseThrow(KnowledgeNotFoundException::new);
        if (target.status() != KnowledgeStatus.DRAFT) throw new IllegalStateException("Only draft versions can be published");
        jdbc.update("UPDATE ai_document_version SET status='DISABLED', updated_by=?, update_time=?, version=version+1 "
                        + "WHERE document_id=? AND tenant_id=? AND status='PUBLISHED' AND id<>? AND deleted=b'0'",
                actor, timestamp(now), text(target.documentId()), tenantId, text(id));
        jdbc.update("UPDATE ai_document_version SET status='PUBLISHED', failure_code=NULL, updated_by=?, update_time=?, "
                + "version=version+1 WHERE id=? AND tenant_id=? AND status='DRAFT' AND deleted=b'0'",
                actor, timestamp(now), text(id), tenantId);
        jdbc.update("UPDATE ai_document SET status='PUBLISHED', active_version_id=?, updated_by=?, update_time=?, "
                + "version=version+1 WHERE id=? AND tenant_id=? AND deleted=b'0'",
                text(id), actor, timestamp(now), text(target.documentId()), tenantId);
        return findVersion(id, tenantId).orElseThrow();
    }

    @Override
    public KnowledgeDocumentVersion disable(UUID id, String tenantId, String actor, Instant now) {
        KnowledgeDocumentVersion target = findVersion(id, tenantId).orElseThrow(KnowledgeNotFoundException::new);
        jdbc.update("UPDATE ai_document_version SET status='DISABLED', updated_by=?, update_time=?, version=version+1 "
                + "WHERE id=? AND tenant_id=? AND deleted=b'0'", actor, timestamp(now), text(id), tenantId);
        jdbc.update("UPDATE ai_document SET status='DISABLED', active_version_id=NULL, updated_by=?, update_time=?, "
                + "version=version+1 WHERE id=? AND tenant_id=? AND active_version_id=? AND deleted=b'0'",
                actor, timestamp(now), text(target.documentId()), tenantId, text(id));
        return findVersion(id, tenantId).orElseThrow();
    }

    @Override
    public void deleteDocument(UUID id, String tenantId, String actor, Instant now) {
        int changed = jdbc.update("UPDATE ai_document SET status='DELETED', active_version_id=NULL, deleted=b'1', "
                + "updated_by=?, update_time=?, version=version+1 WHERE id=? AND tenant_id=? AND deleted=b'0'",
                actor, timestamp(now), text(id), tenantId);
        if (changed == 0) throw new KnowledgeNotFoundException();
    }

    private KnowledgeSpace space(ResultSet rs, int row) throws SQLException {
        return new KnowledgeSpace(uuid(rs, "id"), rs.getString("tenant_id"), rs.getString("name"),
                rs.getString("description"), KnowledgeScope.valueOf(rs.getString("scope")), rs.getString("project_id"),
                KnowledgeStatus.valueOf(rs.getString("status").toUpperCase()), rs.getString("created_by"),
                instant(rs, "create_time"), rs.getString("updated_by"), instant(rs, "update_time"));
    }
    private ManagedKnowledgeDocument document(ResultSet rs, int row) throws SQLException {
        return new ManagedKnowledgeDocument(uuid(rs, "id"), rs.getString("tenant_id"), uuid(rs, "space_id"),
                rs.getString("title"), KnowledgeStatus.valueOf(rs.getString("status").toUpperCase()),
                nullableUuid(rs.getString("active_version_id")), rs.getString("created_by"), instant(rs, "create_time"),
                rs.getString("updated_by"), instant(rs, "update_time"));
    }
    private KnowledgeDocumentVersion version(ResultSet rs, int row) throws SQLException {
        return new KnowledgeDocumentVersion(uuid(rs, "id"), uuid(rs, "document_id"),
                nullableUuid(rs.getString("attachment_id")), rs.getString("tenant_id"), rs.getInt("version_no"),
                KnowledgeStatus.valueOf(rs.getString("status").toUpperCase()), rs.getString("failure_code"),
                rs.getString("created_by"), instant(rs, "create_time"), rs.getString("updated_by"), instant(rs, "update_time"));
    }
    private <T> Optional<T> one(String sql, org.springframework.jdbc.core.RowMapper<T> mapper, Object... args) {
        List<T> values = jdbc.query(sql, mapper, args); return values.stream().findFirst();
    }
    private static UUID uuid(ResultSet rs, String column) throws SQLException { return UUID.fromString(rs.getString(column)); }
    private static UUID nullableUuid(String value) { return value == null ? null : UUID.fromString(value); }
    private static Instant instant(ResultSet rs, String column) throws SQLException { return rs.getTimestamp(column).toInstant(); }
    private static Timestamp timestamp(Instant value) { return Timestamp.from(value); }
    private static String text(UUID value) { return value == null ? null : value.toString(); }
}
