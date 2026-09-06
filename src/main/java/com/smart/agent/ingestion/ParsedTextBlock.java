package com.smart.agent.ingestion;

public record ParsedTextBlock(String text, Integer pageNumber, String sheetName, String sectionTitle) {
    public ParsedTextBlock {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (pageNumber != null && pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be positive");
        }
        sheetName = normalize(sheetName);
        sectionTitle = normalize(sectionTitle);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
