package com.smart.agent.tool.material;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.*;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.MaterialRefundDomainContributor;
import com.smart.agent.common.error.AgentException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class MaterialRefundCompareTool implements AgentTool<MaterialRefundCompareInput, Object> {
    private final MaterialRefundBusinessClient client;
    private final ProjectReferenceResolver projects;
    public MaterialRefundCompareTool(MaterialRefundBusinessClient client, ProjectReferenceResolver projects) {
        this.client = client; this.projects = projects;
    }
    @Override public String key() { return "materialRefund.compare"; }
    @Override public Class<MaterialRefundCompareInput> inputType() { return MaterialRefundCompareInput.class; }
    @Override public String requiredPermission() { return MaterialRefundDomainContributor.PERMISSION; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() {
        return "实时比较审批完成的材料退货退款单数、明细条数、退货退款金额；不代表退款已到账；仅审批完成的项目仓退货退款，不含仅退货、公司仓、付款申请或报销。"
                + "YOY同比上年同期，MOM环比上月/上季度/上年，CUSTOM自定义两组。current本期，baseline仅CUSTOM提供。"
                + "periodUnit为MONTH/QUARTER/YEAR，YOY任意区间用RANGE；自然期间从首日开始，可截止已过日期。"
                + "日期为createDate申请创建日期或projectCreateDate项目创建日期、returnDate材料明细的退货时间；按退货时间仅统计区间内明细，申请单按有匹配明细去重，不能整单计入；不明确先clarifyDate，相对日期先取当前时间。"
                + "两组必须明确同一日期维度和完整起止日期，不支持无日期全历史比较；分别按可见项目范围实时全量汇总；程序计算差额和增长率，零基准不算比例。"
                + "回复必须说明实际两组日期、项目范围及日期含义，不得用一页明细相加或历史金额替代。缺少比较期间先追问。";
    }
    @Override public String argumentsSchemaJson() {
        String group = """
            {"type":"object","properties":{
              "projectId":{"type":"string","description":"真实项目ID，不是列表序号"},
              "projectCode":{"type":"string"},"projectName":{"type":"string","description":"精确项目名称，同名追问"},
              "projectNameMatch":{"type":"string","enum":["exact","contains"],"description":"仅用户明确要求包含匹配时用contains"},
              "dateField":{"type":"string","enum":["createDate","projectCreateDate","returnDate"]},
              "startDate":{"type":"string","description":"yyyy-MM-dd，含当天"},
              "endDate":{"type":"string","description":"yyyy-MM-dd，含当天"}
            },"required":["dateField","startDate","endDate"],"additionalProperties":false}
            """;
        return "{\"type\":\"object\",\"properties\":{\"comparisonType\":{\"type\":\"string\",\"enum\":[\"YOY\",\"MOM\",\"CUSTOM\"]},"
                + "\"periodUnit\":{\"type\":\"string\",\"enum\":[\"MONTH\",\"QUARTER\",\"YEAR\",\"RANGE\"]},"
                + "\"current\":" + group + ",\"baseline\":" + group
                + "},\"required\":[\"comparisonType\",\"current\"],\"additionalProperties\":false}";
    }
    @Override public Object execute(MaterialRefundCompareInput input, ToolContext context) {
        if (!context.permissions().contains(requiredPermission()))
            throw new AgentException("AGENT_TOOL_FORBIDDEN", HttpStatus.FORBIDDEN, "当前账号尚未开通材料退货退款的查看权限");
        Object current = resolve(input.current(), "current", context);
        if (current instanceof ClarificationPrompt) return current;
        Object baseline = input.baseline() == null ? null : resolve(input.baseline(), "baseline", context);
        if (baseline instanceof ClarificationPrompt) return baseline;
        var a = (MaterialRefundQueryInput) current;
        var b = (MaterialRefundQueryInput) baseline;
        var result = client.compare(context, new MaterialRefundCompareInput(input.comparisonType(), input.periodUnit(), a, b));
        var filters = new java.util.ArrayList<>(result.appliedFilters());
        if (a.projectId() != null) filters.add(java.util.Map.of("comparisonGroup", "current", "projectId", a.projectId()));
        if (b != null && b.projectId() != null) filters.add(java.util.Map.of("comparisonGroup", "baseline", "projectId", b.projectId()));
        return new ProjectQueryResult(result.mode(), result.page(), result.pageSize(), result.total(), result.hasNext(),
                result.columns(), result.rows(), filters);
    }
    private Object resolve(MaterialRefundQueryInput input, String group, ToolContext context) {
        if ("contains".equals(input.projectNameMatch()) || input.projectId() == null && input.projectCode() == null && input.projectName() == null)
            return input;
        var matches = projects.resolve(context, input.projectId(), input.projectCode(), input.projectName());
        if (matches.size() > 1)
            return new ClarificationPrompt("请确认" + ("current".equals(group) ? "本期" : "对比期") + "要比较的项目：",
                    "COMPARISON_PROJECT_REFERENCE", matches.stream().limit(5).map(item -> new ClarificationPrompt.Option(
                            group + ":project:" + item.projectId(), item.projectName() + "（编号：" + item.projectCode() + "）",
                            MaterialRefundDomainContributor.CODE)).toList());
        return input.withProjectId(matches.getFirst().projectId());
    }
}
