package com.smart.agent.knowledge;

public interface KnowledgeRepository {
    void save(KnowledgeDocument document);

    void markIndexingSucceeded(String documentId);

    void markIndexingFailed(String documentId, String failureCode);
}
