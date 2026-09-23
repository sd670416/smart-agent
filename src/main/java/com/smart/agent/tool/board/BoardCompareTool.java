package com.smart.agent.tool.board;

import com.smart.agent.tool.AgentTool;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.ToolRisk;
import org.springframework.stereotype.Component;

@Component
public class BoardCompareTool implements AgentTool<BoardCompareInput, Object> {
    private final BoardBusinessClient client;
    public BoardCompareTool(BoardBusinessClient client) { this.client = client; }
    public String key() { return "board.compare"; }
    public Class<BoardCompareInput> inputType() { return BoardCompareInput.class; }
    public String requiredPermission() { return ""; }
    public ToolRisk risk() { return ToolRisk.L1; }
    public String description() { return "比较同一看板指标的两组或多组筛选结果"; }
    public String argumentsSchemaJson() { return "{\"type\":\"object\",\"properties\":{\"boardType\":{\"type\":\"string\"},\"metric\":{\"type\":\"string\"},\"groups\":{\"type\":\"array\"},\"dimension\":{\"type\":\"string\"},\"page\":{\"type\":\"integer\"},\"pageSize\":{\"type\":\"integer\"}},\"required\":[\"boardType\",\"metric\",\"groups\"],\"additionalProperties\":false}"; }
    public Object execute(BoardCompareInput input, ToolContext context) { return client.compare(context, input); }
}
