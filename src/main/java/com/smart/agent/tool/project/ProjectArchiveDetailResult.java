package com.smart.agent.tool.project;

import java.util.List;
import java.util.Map;

public record ProjectArchiveDetailResult(
        String projectId,
        String projectName,
        List<Section> sections) {

    public record Section(
            String key,
            String title,
            String status,
            Map<String, Object> summary,
            List<Map<String, Object>> items,
            String message) {
    }
}
