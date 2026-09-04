package com.smart.agent.ingestion;

public record ParseLimits(
        long maxSourceBytes,
        int maxExtractedCharacters,
        int maxPages,
        int maxArchiveEntries,
        long maxExpandedBytes,
        int maxImageWidth,
        int maxImageHeight,
        long maxImagePixels) {

    public ParseLimits(long maxSourceBytes, int maxExtractedCharacters, int maxPages,
            int maxArchiveEntries, long maxExpandedBytes) {
        this(maxSourceBytes, maxExtractedCharacters, maxPages, maxArchiveEntries, maxExpandedBytes,
                20_000, 20_000, 100_000_000L);
    }

    public ParseLimits {
        if (maxSourceBytes < 1 || maxExtractedCharacters < 1 || maxPages < 1
                || maxArchiveEntries < 1 || maxExpandedBytes < 1 || maxImageWidth < 1
                || maxImageHeight < 1 || maxImagePixels < 1) {
            throw new IllegalArgumentException("parse limits must be positive");
        }
    }

    public static ParseLimits defaults() {
        return new ParseLimits(200L * 1024 * 1024, 2_000_000, 5_000, 10_000,
                500L * 1024 * 1024, 20_000, 20_000, 100_000_000L);
    }
}
