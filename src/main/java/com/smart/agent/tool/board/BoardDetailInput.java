package com.smart.agent.tool.board;

import java.util.Map;

public record BoardDetailInput(String boardType, String metric, String resultId, String projectId,
                               String recordId, Map<String, Object> filters, Integer page, Integer pageSize) {
}
