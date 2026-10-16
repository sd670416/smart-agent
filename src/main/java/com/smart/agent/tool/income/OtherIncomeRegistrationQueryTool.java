package com.smart.agent.tool.income;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.ProjectReferenceResolver;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.OtherIncomeRegistrationDomainContributor;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class OtherIncomeRegistrationQueryTool implements AgentTool<OtherIncomeRegistrationQueryInput, Object> {
    private final OtherIncomeRegistrationBusinessClient client;
    private final ProjectReferenceResolver projectReferences;
    public OtherIncomeRegistrationQueryTool(OtherIncomeRegistrationBusinessClient client, ProjectReferenceResolver projectReferences) {
        this.client = client;
        this.projectReferences = projectReferences;
    }
    @Override public String key() { return "otherIncomeRegistration.query"; }
    @Override public Class<OtherIncomeRegistrationQueryInput> inputType() { return OtherIncomeRegistrationQueryInput.class; }
    @Override public String requiredPermission() { return "menu:finance:other-income-registration"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    // Scope is checked against the canonical ID after resolving legacy code/name inputs, below.
    @Override public Optional<String> projectId(OtherIncomeRegistrationQueryInput input) { return Optional.empty(); }
    @Override public String description() {
        return "查询当前用户可见项目中审批完成的其他收入登记：分页列表、登记数量及到账金额合计。"
                + "其他回款对应其他收入登记，不含回款登记或付款申请，不能称为全部已收项目款。金额合计使用summary，不能汇总当前页或累计回款字段。"
                + "支持到账日期receivedDate、登记创建日期createDate、项目创建日期projectCreateDate；日期不明确先调用otherIncomeRegistration.clarifyDate。待办或流程使用approval工具。";
    }
    @Override public String argumentsSchemaJson() {
        return """
                {"type":"object","properties":{
                  "projectId":{"type":"string","description":"真实项目ID，不能填写项目编号。追问当前项目时继承已确认的真实项目ID"},
                  "projectCode":{"type":"string","description":"项目编号，例如20260708001；由程序在可见项目中精确定位"},
                  "projectName":{"type":"string","description":"默认精确项目名称筛选，同名多项时追问确认，不得默认包含匹配扩大范围"},
                  "projectNameMatch":{"type":"string","description":"仅允许exact或contains，默认exact。只有用户明确要求名称包含或模糊匹配时用contains，不能作为失败后的降级重试"},
                  "registrationCode":{"type":"string","description":"其他收入登记编号包含筛选"},
                  "mode":{"type":"string","description":"detail分页列表；summary登记数量及到账金额精确合计"},
                  "dateField":{"type":"string","description":"receivedDate到账日期、createDate登记创建日期、projectCreateDate项目创建日期。日期不明确先追问"},
                  "startDate":{"type":"string","description":"开始日期 yyyy-MM-dd，含当天"},
                  "endDate":{"type":"string","description":"结束日期 yyyy-MM-dd，含当天"},
                  "page":{"type":"integer","description":"从1开始"},"pageSize":{"type":"integer","description":"1到100，默认20"}
                },"additionalProperties":false}
                """;
    }
    @Override public Object execute(OtherIncomeRegistrationQueryInput input, ToolContext context) {
        if ("contains".equals(input.projectNameMatch())
                || input.projectId() == null && input.projectCode() == null && input.projectName() == null)
            return client.query(context, input);
        var matches = projectReferences.resolve(context, input.projectId(), input.projectCode(), input.projectName());
        if (matches.size() > 1) {
            return new ClarificationPrompt("匹配到多个可见项目，请确认要查看哪个项目的其他收入登记（也可补充准确项目编号）：",
                    "PROJECT_REFERENCE", matches.stream().limit(5).map(item -> new ClarificationPrompt.Option(
                            "project:" + item.projectId(), item.projectName() + "（编号：" + item.projectCode() + "）",
                            OtherIncomeRegistrationDomainContributor.CODE)).toList());
        }
        String resolvedId = matches.getFirst().projectId();
        var result = client.query(context, input.withProjectId(resolvedId));
        var filters = new java.util.ArrayList<>(result.appliedFilters());
        filters.add(java.util.Map.of("field", "projectId", "op", "eq", "value", resolvedId));
        return new com.smart.agent.tool.project.ProjectQueryResult(result.mode(), result.page(), result.pageSize(),
                result.total(), result.hasNext(), result.columns(), result.rows(), filters);
    }
}
