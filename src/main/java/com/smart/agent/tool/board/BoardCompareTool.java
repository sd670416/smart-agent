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
    public String description() { return "实时比较经营看板同一指标的两至五组筛选结果，boardType=manage；groups 每组提供 projectId 或 dateBetweenList。数字卡返回各组数值及差额；分布图和分析图返回各组维度行及同维度差额；两张明细表不能直接比较。不要自行计算或将图表压成单个总数。"; }
    public String argumentsSchemaJson() { return "{\"type\":\"object\",\"properties\":{\"boardType\":{\"type\":\"string\"},\"metric\":{\"type\":\"string\"},\"groups\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"boardType\":{\"type\":\"string\"},\"metric\":{\"type\":\"string\"},\"filters\":{\"type\":\"object\"},\"page\":{\"type\":\"integer\"},\"pageSize\":{\"type\":\"integer\"}},\"additionalProperties\":false}},\"dimension\":{\"type\":\"string\"},\"page\":{\"type\":\"integer\"},\"pageSize\":{\"type\":\"integer\"}},\"required\":[\"boardType\",\"metric\",\"groups\"],\"additionalProperties\":false}"; }
    public Object execute(BoardCompareInput input, ToolContext context) { return client.compare(context, input); }
}
