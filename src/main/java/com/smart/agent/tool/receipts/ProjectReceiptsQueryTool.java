package com.smart.agent.tool.receipts;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.ProjectReferenceResolver;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.ProjectReceiptsDomainContributor;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProjectReceiptsQueryTool implements AgentTool<ProjectReceiptsQueryInput, Object> {
    private final ProjectReceiptsBusinessClient client;
    private final ProjectReferenceResolver projectReferences;
    public ProjectReceiptsQueryTool(ProjectReceiptsBusinessClient client, ProjectReferenceResolver projectReferences) {
        this.client = client;
        this.projectReferences = projectReferences;
    }
    @Override public String key() { return "projectReceipts.query"; }
    @Override public Class<ProjectReceiptsQueryInput> inputType() { return ProjectReceiptsQueryInput.class; }
    @Override public String requiredPermission() { return "menu:finance:other-income-registration"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    // Scope is checked against the canonical ID after resolving legacy code/name inputs, below.
    @Override public Optional<String> projectId(ProjectReceiptsQueryInput input) { return Optional.empty(); }
    @Override public String description() {
        return "实时查询已收项目款：回款登记与其他收入登记的到账金额分项、单据数量及合计。必须同时具有两类菜单权限。"
                + "服务端精确汇总，不自行相加历史结果或当前页，不把任一来源单独当作完整总额。"
                + "仅可见项目且审批完成；不含未关联项目的公司级收入。日期不明确先调用projectReceipts.clarifyDate。";
    }
    @Override public String argumentsSchemaJson() {
        return """
                {"type":"object","properties":{
                  "projectId":{"type":"string","description":"真实项目ID，不能填写项目编号。追问当前项目时继承已确认的真实项目ID"},
                  "projectCode":{"type":"string","description":"项目编号，例如20260708001；由程序在可见项目中精确定位"},
                  "projectName":{"type":"string","description":"默认精确项目名称筛选，同名多项时追问确认，不得默认包含匹配扩大范围"},
                  "projectNameMatch":{"type":"string","description":"仅允许exact或contains，默认exact。只有用户明确要求名称包含或模糊匹配时用contains，不能作为失败后的降级重试"},
                  "dateField":{"type":"string","description":"receivedDate到账日期、createDate登记创建日期、projectCreateDate项目创建日期。日期不明确先追问"},
                  "startDate":{"type":"string","description":"开始日期 yyyy-MM-dd，含当天"},
                  "endDate":{"type":"string","description":"结束日期 yyyy-MM-dd，含当天"}
                },"additionalProperties":false}
                """;
    }
    @Override public Object execute(ProjectReceiptsQueryInput input, ToolContext context) {
        if (!ProjectReceiptsDomainContributor.canAccess(context.permissions()))
            throw new com.smart.agent.common.error.AgentException("AGENT_TOOL_FORBIDDEN",
                    org.springframework.http.HttpStatus.FORBIDDEN, "Both financial menu permissions are required");
        if ("contains".equals(input.projectNameMatch())
                || input.projectId() == null && input.projectCode() == null && input.projectName() == null)
            return client.query(context, input);
        var matches = projectReferences.resolve(context, input.projectId(), input.projectCode(), input.projectName());
        if (matches.size() > 1) {
            return new ClarificationPrompt("匹配到多个可见项目，请确认要查看哪个项目的已收项目款（也可补充准确项目编号）：",
                    "PROJECT_REFERENCE", matches.stream().limit(5).map(item -> new ClarificationPrompt.Option(
                            "project:" + item.projectId(), item.projectName() + "（编号：" + item.projectCode() + "）",
                            ProjectReceiptsDomainContributor.CODE)).toList());
        }
        String resolvedId = matches.getFirst().projectId();
        var result = client.query(context, input.withProjectId(resolvedId));
        var filters = new java.util.ArrayList<>(result.appliedFilters());
        filters.add(java.util.Map.of("field", "projectId", "op", "eq", "value", resolvedId));
        return new com.smart.agent.tool.project.ProjectQueryResult(result.mode(), result.page(), result.pageSize(),
                result.total(), result.hasNext(), result.columns(), result.rows(), filters);
    }
}
