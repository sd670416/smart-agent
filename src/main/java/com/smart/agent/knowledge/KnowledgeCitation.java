package com.smart.agent.knowledge;

public record KnowledgeCitation(
        String documentId,
        String versionId,
        String title,
        Integer pageNumber,
        String sectionTitle,
        String excerpt,
        double score,
        String citationToken) {
    public KnowledgeCitation {
        documentId = requireText(documentId, "documentId");
        versionId = requireText(versionId, "versionId");
        title = requireText(title, "title");
        excerpt = requireText(excerpt, "excerpt");
        citationToken = requireText(citationToken, "citationToken");
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
        if (pageNumber != null && pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be positive");
        }
        if (sectionTitle != null && sectionTitle.isBlank()) {
            sectionTitle = null;
        }
    }

    public String location() {
        if (pageNumber != null && sectionTitle != null) {
            return "第" + pageNumber + "页 / " + sectionTitle;
        }
        if (pageNumber != null) {
            return "第" + pageNumber + "页";
        }
        return sectionTitle == null ? "文档正文" : sectionTitle;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
