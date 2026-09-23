package com.smart.agent.tool.board;

import com.smart.agent.tool.AgentTool;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.ToolRisk;
import org.springframework.stereotype.Component;

@Component
public class BoardQueryTool implements AgentTool<BoardQueryInput, Object> {
    private final BoardBusinessClient client;
    public BoardQueryTool(BoardBusinessClient client) { this.client = client; }
    public String key() { return "board.query"; }
    public Class<BoardQueryInput> inputType() { return BoardQueryInput.class; }
    public String requiredPermission() { return ""; }
    public ToolRisk risk() { return ToolRisk.L1; }
    public String description() { return "按指定看板的原有筛选条件查询汇总或明细；权限仅由对应看板菜单决定"; }
    public String argumentsSchemaJson() { return "{\"type\":\"object\",\"properties\":{\"boardType\":{\"type\":\"string\"},\"metric\":{\"type\":\"string\"},\"filters\":{\"type\":\"object\"},\"dimension\":{\"type\":\"string\"},\"page\":{\"type\":\"integer\"},\"pageSize\":{\"type\":\"integer\"},\"orderBy\":{\"type\":\"string\"},\"orderDirection\":{\"type\":\"string\"}},\"required\":[\"boardType\",\"metric\"],\"additionalProperties\":false}"; }
    public Object execute(BoardQueryInput input, ToolContext context) { return client.query(context, input); }
}
