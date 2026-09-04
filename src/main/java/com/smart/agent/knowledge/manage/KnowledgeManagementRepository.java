package com.smart.agent.knowledge.manage;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeManagementRepository {
    KnowledgeSpace saveSpace(KnowledgeSpace space);
    Optional<KnowledgeSpace> findSpace(UUID id, String tenantId);
    void lockSpace(UUID spaceId, String tenantId);
    void lockAttachment(UUID attachmentId, String tenantId, String userId);
    PageResult<KnowledgeSpace> listSpaces(String tenantId, java.util.Set<String> projectIds, int page, int size);
    ManagedKnowledgeDocument saveDocument(ManagedKnowledgeDocument document);
    Optional<ManagedKnowledgeDocument> findDocument(UUID id, String tenantId);
    Optional<ManagedKnowledgeDocument> findDocumentBySpaceAndTitle(UUID spaceId, String title, String tenantId);
    PageResult<ManagedKnowledgeDocument> listDocuments(UUID spaceId, String tenantId, int page, int size);
    int nextVersionNumber(UUID documentId, String tenantId);
    void lockDocument(UUID documentId, String tenantId);
    KnowledgeDocumentVersion saveVersion(KnowledgeDocumentVersion version);
    Optional<KnowledgeDocumentVersion> findVersion(UUID id, String tenantId);
    Optional<KnowledgeDocumentVersion> findVersionByAttachment(UUID attachmentId, String tenantId);
    Optional<KnowledgeDocumentVersion> findActiveVersion(UUID documentId, String tenantId);
    Optional<KnowledgeDocumentVersion> findLatestVersion(UUID documentId, String tenantId);
    KnowledgeDocumentVersion publish(UUID id, String tenantId, String actor, Instant now);
    KnowledgeDocumentVersion disable(UUID id, String tenantId, String actor, Instant now);
    void deleteDocument(UUID id, String tenantId, String actor, Instant now);
}
