package com.smart.agent.knowledge;

import java.util.List;

public interface VectorIndex {
    void upsert(List<IndexedChunk> chunks);

    List<VectorHit> search(VectorSearchQuery query);

    void deleteDocument(String tenantId, String documentId);
}
