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
    public String description() { return "实时查询经营看板指标，不要用历史结果代替查询。boardType=manage；metric 可选 projectCount(项目总数)、regionDistribution(项目区域分布)、contractAmount(建设合同总金额)、settlementAmount(累计结算金额)、receivedPayment(已收项目款)、fundingAdvance(当前项目垫资)、profitAmount(实际项目利润)、projectTypeCount(各类型项目数量分布)、projectTypeContractAmount(各类型项目合同金额分布)、projectStatusDistribution(项目状态分布)、progress(项目完成进度)、costShare(项目各项成本费用占比)、companyOperation(各自持公司经营数据)、fundingAnalysis(项目垫资分析)、profitCompare(项目利润对比分析)、fundingDetail(项目垫资明细)、profitDetail(项目利润对比明细)。filters 仅支持 projectId 和 dateBetweenList(开始、结束日期)。数字指标返回 value；图表返回按维度的 rows；明细返回分页 rows。"; }
    public String argumentsSchemaJson() { return "{\"type\":\"object\",\"properties\":{\"boardType\":{\"type\":\"string\"},\"metric\":{\"type\":\"string\"},\"filters\":{\"type\":\"object\"},\"dimension\":{\"type\":\"string\"},\"page\":{\"type\":\"integer\"},\"pageSize\":{\"type\":\"integer\"},\"orderBy\":{\"type\":\"string\"},\"orderDirection\":{\"type\":\"string\"}},\"required\":[\"boardType\",\"metric\"],\"additionalProperties\":false}"; }
    public Object execute(BoardQueryInput input, ToolContext context) { return client.query(context, input); }
}
