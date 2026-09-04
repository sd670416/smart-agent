package com.smart.agent.knowledge;

import java.util.List;

public interface VectorIndex {
    void upsert(List<IndexedChunk> chunks);

    default void upsert(VectorNamespace namespace, List<IndexedChunk> chunks) {
        if (namespace != VectorNamespace.KNOWLEDGE) {
            throw new UnsupportedOperationException("Vector index does not support temporary namespaces");
        }
        upsert(chunks);
    }

    List<VectorHit> search(VectorSearchQuery query);

    default List<VectorHit> search(VectorNamespace namespace, VectorSearchQuery query) {
        if (namespace != VectorNamespace.KNOWLEDGE) {
            throw new UnsupportedOperationException("Vector index does not support temporary namespaces");
        }
        return search(query);
    }

    void deleteDocument(String tenantId, String documentId);

    default void deleteDocument(VectorNamespace namespace, String tenantId, String documentId) {
        if (namespace != VectorNamespace.KNOWLEDGE) {
            throw new UnsupportedOperationException("Vector index does not support temporary namespaces");
        }
        deleteDocument(tenantId, documentId);
    }

    default void deleteAttachment(VectorNamespace namespace, String tenantId, String attachmentId) {
        throw new UnsupportedOperationException("Vector index does not support attachment deletion");
    }
}
