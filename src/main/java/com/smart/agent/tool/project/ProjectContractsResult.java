package com.smart.agent.tool.project;

import java.util.List;

public record ProjectContractsResult(String projectId, String projectName,
                                     List<ProjectArchiveDetailResult.Section> sections) {
    public ProjectContractsResult {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
