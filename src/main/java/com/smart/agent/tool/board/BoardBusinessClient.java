package com.smart.agent.tool.board;

import com.smart.agent.tool.ToolContext;

public interface BoardBusinessClient {
    Object query(ToolContext context, BoardQueryInput input);
    Object compare(ToolContext context, BoardCompareInput input);
    Object detail(ToolContext context, BoardDetailInput input);
}
