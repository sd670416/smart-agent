package com.smart.agent.tool.contract;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.*;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.ConstructionContractDomainContributor;
import com.smart.agent.common.error.AgentException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ConstructionContractCompareTool implements AgentTool<ConstructionContractCompareInput, Object> {
    private final ConstructionContractBusinessClient client;
    private final ProjectReferenceResolver projects;
    public ConstructionContractCompareTool(ConstructionContractBusinessClient client, ProjectReferenceResolver projects) {
        this.client = client; this.projects = projects;
    }
    @Override public String key() { return "constructionContract.compare"; }
    @Override public Class<ConstructionContractCompareInput> inputType() { return ConstructionContractCompareInput.class; }
    @Override public String requiredPermission() { return "menu:project"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() {
        return "实时比较施工合同数量和建设合同金额。YOY同比上年同期，MOM环比上月/上季度/上年，CUSTOM自定义两组。"
                + "current为本期，baseline仅CUSTOM提供；自动对比期由服务端推导。periodUnit为MONTH/QUARTER/YEAR，YOY任意日期用RANGE。"
                + "自然月季年从期间首日开始，可截止已过日期；不把部分本期与完整上期比较。日期维度不明先clarifyDate；相对日期先取当前时间。"
                + "两组同一日期维度、项目菜单权限、各自可见项目范围。程序计算本期减对比期及增长率，零基准不计算比例。"
                + "必须展示实际两组日期及项目范围，不能自行计算或引用历史金额。缺少比较对象先追问。";
    }
    @Override public String argumentsSchemaJson() {
        String group = """
            {"type":"object","properties":{
              "contractName":{"type":"string","description":"合同名称包含匹配"},
              "projectId":{"type":"string","description":"真实项目ID，不是编号"},
              "projectCode":{"type":"string","description":"项目编号，程序精确定位"},
              "projectName":{"type":"string","description":"项目名称，默认精确匹配，同名追问"},
              "projectNameMatch":{"type":"string","description":"仅exact或contains；仅用户明确要求包含匹配时用contains"},
              "dateField":{"type":"string","description":"仅signingDate合同签订日期、createDate合同创建日期、projectCreateDate项目创建日期"},
              "startDate":{"type":"string","description":"含当天，yyyy-MM-dd"},
              "endDate":{"type":"string","description":"含当天，yyyy-MM-dd"}
            },"additionalProperties":false}
            """;
        return "{\"type\":\"object\",\"properties\":{\"comparisonType\":{\"type\":\"string\",\"description\":\"仅YOY同比、MOM环比、CUSTOM自定义\"},"
                + "\"periodUnit\":{\"type\":\"string\",\"description\":\"仅MONTH月、QUARTER季度、YEAR年、RANGE任意区间同比\"},"
                + "\"current\":" + group + ",\"baseline\":" + group
                + "},\"required\":[\"comparisonType\",\"current\"],\"additionalProperties\":false}";
    }
    @Override public Object execute(ConstructionContractCompareInput input, ToolContext context) {
        if (!context.permissions().contains("menu:project"))
            throw new AgentException("AGENT_PROJECT_MENU_FORBIDDEN", HttpStatus.FORBIDDEN, "Project menu permission required");
        Object current = resolve(summary(input.current()), "current", context);
        if (current instanceof ClarificationPrompt) return current;
        Object baseline = input.baseline() == null ? null : resolve(summary(input.baseline()), "baseline", context);
        if (baseline instanceof ClarificationPrompt) return baseline;
        var currentInput = (ConstructionContractQueryInput)current;
        var baselineInput = (ConstructionContractQueryInput)baseline;
        var result = client.compare(context, new ConstructionContractCompareInput(input.comparisonType(), input.periodUnit(),
                currentInput, baselineInput));
        var filters = new java.util.ArrayList<>(result.appliedFilters());
        if (currentInput.projectId() != null) filters.add(java.util.Map.of("comparisonGroup", "current", "projectId", currentInput.projectId()));
        if (baselineInput != null && baselineInput.projectId() != null)
            filters.add(java.util.Map.of("comparisonGroup", "baseline", "projectId", baselineInput.projectId()));
        return new ProjectQueryResult(result.mode(), result.page(), result.pageSize(), result.total(), result.hasNext(),
                result.columns(), result.rows(), filters);
    }
    private ConstructionContractQueryInput summary(ConstructionContractQueryInput input) {
        return new ConstructionContractQueryInput(input.projectId(), input.projectName(), input.contractName(),
                "summary", input.dateField(), input.startDate(), input.endDate(), 1, 20,
                input.projectCode(), input.projectNameMatch(), null, input.projectType(), input.projectTypeName());
    }
    private Object resolve(ConstructionContractQueryInput input, String group, ToolContext context) {
        if ("contains".equals(input.projectNameMatch()) || input.projectId() == null && input.projectName() == null && input.projectCode() == null)
            return input;
        var matches = projects.resolve(context, input.projectId(), input.projectCode(), input.projectName());
        if (matches.size() > 1)
            return new ClarificationPrompt("请确认" + ("current".equals(group) ? "本期" : "对比期") + "要比较的项目：",
                    "COMPARISON_PROJECT_REFERENCE", matches.stream().limit(5).map(item -> new ClarificationPrompt.Option(
                            group + ":project:" + item.projectId(), item.projectName() + "（编号：" + item.projectCode() + "）",
                            ConstructionContractDomainContributor.CODE)).toList());
        return input.withProjectId(matches.getFirst().projectId());
    }
}
