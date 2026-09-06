package com.smart.agent.ingestion;

import java.util.List;
import java.util.Map;

public record DocumentParseResult(
        DocumentParseStatus status,
        DetectedContentType detectedType,
        String mediaType,
        List<ParsedTextBlock> blocks,
        Map<String, String> metadata,
        String failureCode) {

    public DocumentParseResult {
        if (status == null || detectedType == null || mediaType == null || mediaType.isBlank()) {
            throw new IllegalArgumentException("parse result identity is required");
        }
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (status == DocumentParseStatus.PARSED && failureCode != null) {
            throw new IllegalArgumentException("parsed result cannot have a failure code");
        }
        if (status != DocumentParseStatus.PARSED && (failureCode == null || failureCode.isBlank())) {
            throw new IllegalArgumentException("non-parsed result requires a failure code");
        }
    }
}
