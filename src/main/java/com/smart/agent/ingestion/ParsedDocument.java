package com.smart.agent.ingestion;

import java.util.List;
import java.util.Map;

public record ParsedDocument(List<ParsedTextBlock> blocks, Map<String, String> metadata) {
    public ParsedDocument {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
