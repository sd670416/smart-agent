package com.smart.agent.tool.board;

import java.util.List;

public record BoardCompareInput(String boardType, String metric, List<BoardQueryInput> groups,
                                String dimension, Integer page, Integer pageSize) {
}
