package com.smart.agent.knowledge;

import java.util.Map;

public record VectorHit(
        String chunkId,
        String documentId,
        double score,
        String content,
        Map<String, String> metadata) {

    public VectorHit {
        metadata = Map.copyOf(metadata);
    }
}
