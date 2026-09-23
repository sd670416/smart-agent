package com.smart.agent.tool.board;

import java.util.Map;

public record BoardQueryInput(String boardType, String metric, Map<String, Object> filters,
                              String dimension, Integer page, Integer pageSize,
                              String orderBy, String orderDirection) {
}
