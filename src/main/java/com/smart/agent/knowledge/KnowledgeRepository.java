package com.smart.agent.knowledge;

import java.util.Collection;
import java.util.List;

public interface KnowledgeRepository {
    void save(KnowledgeDocument document);

    default void saveManagedDocument(KnowledgeDocument document) {
        save(document);
    }

    void markIndexingSucceeded(String documentId);

    void markIndexingFailed(String documentId, String failureCode);

    List<KnowledgeChunkMetadata> findPublishedChunks(String tenantId, Collection<String> chunkIds);
}
