package com.smart.agent.tool.board;

import com.smart.agent.tool.AgentTool;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.ToolRisk;
import org.springframework.stereotype.Component;

@Component
public class BoardDetailTool implements AgentTool<BoardDetailInput, Object> {
    private final BoardBusinessClient client;
    public BoardDetailTool(BoardBusinessClient client) { this.client = client; }
    public String key() { return "board.detail"; }
    public Class<BoardDetailInput> inputType() { return BoardDetailInput.class; }
    public String requiredPermission() { return ""; }
    public ToolRisk risk() { return ToolRisk.L1; }
    public String description() { return "查看看板指标对应的明细数据"; }
    public String argumentsSchemaJson() { return "{\"type\":\"object\",\"properties\":{\"boardType\":{\"type\":\"string\"},\"metric\":{\"type\":\"string\"},\"resultId\":{\"type\":\"string\"},\"projectId\":{\"type\":\"string\"},\"recordId\":{\"type\":\"string\"},\"filters\":{\"type\":\"object\"},\"page\":{\"type\":\"integer\"},\"pageSize\":{\"type\":\"integer\"}},\"required\":[\"boardType\",\"metric\"],\"additionalProperties\":false}"; }
    public Object execute(BoardDetailInput input, ToolContext context) { return client.detail(context, input); }
}
