package com.smart.agent.tool.output;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.ProjectReferenceResolver;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.ProjectOutputDomainContributor;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProjectOutputQueryTool implements AgentTool<ProjectOutputQueryInput, Object> {
    private final ProjectOutputBusinessClient client;
    private final ProjectReferenceResolver projectReferences;
    public ProjectOutputQueryTool(ProjectOutputBusinessClient client, ProjectReferenceResolver projectReferences) {
        this.client = client;
        this.projectReferences = projectReferences;
    }
    @Override public String key() { return "projectOutput.query"; }
    @Override public Class<ProjectOutputQueryInput> inputType() { return ProjectOutputQueryInput.class; }
    @Override public String requiredPermission() { return "menu:project"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    // Scope is checked against the canonical ID after resolving legacy code/name inputs, below.
    @Override public Optional<String> projectId(ProjectOutputQueryInput input) { return Optional.empty(); }
    @Override public String description() {
        return "查询当前用户可见项目的审批完成工程产值单：明细、数量及本期完成产值合计。"
                + "不查询待办或劳务或专包产值。日期维度不明确时先调用 projectOutput.clarifyDate；"
                + "原经营看板累计结算金额来自工程产值本期完成产值之和，不是合同最终结算，回答须说明来源。"
                + "原经营看板指标日期是项目创建日期，明确产值单创建日期时用 createDate。数量或金额合计用 summary，不对分页明细自行求总额。";
    }
    @Override public String argumentsSchemaJson() {
        return """
                {"type":"object","properties":{
                  "projectId":{"type":"string","description":"真实项目ID，不能填写项目编号。追问当前项目时继承已确认的真实项目ID"},
                  "projectCode":{"type":"string","description":"项目编号，例如20260708001；由程序在可见项目中精确定位"},
                  "projectName":{"type":"string","description":"默认精确项目名称筛选，同名多项时追问确认，不得默认包含匹配扩大范围"},
                  "projectNameMatch":{"type":"string","description":"仅允许exact或contains，默认exact。只有用户明确要求名称包含或模糊匹配时用contains，不能作为失败后的降级重试"},
                  "outputReportNo":{"type":"string","description":"产值单编号包含筛选"},
                  "mode":{"type":"string","description":"只允许 detail 或 summary：detail 返回分页明细；summary 返回产值单数量及本期完成产值总计"},
                  "dateField":{"type":"string","description":"只允许 projectCreateDate（项目创建日期）、createDate（产值单创建日期）。日期不明确先追问"},
                  "startDate":{"type":"string","description":"开始日期 yyyy-MM-dd，含当天"},
                  "endDate":{"type":"string","description":"结束日期 yyyy-MM-dd，含当天"},
                  "page":{"type":"integer","description":"从1开始"},"pageSize":{"type":"integer","description":"1到100，默认20"}
                },"additionalProperties":false}
                """;
    }
    @Override public Object execute(ProjectOutputQueryInput input, ToolContext context) {
        if ("contains".equals(input.projectNameMatch())
                || input.projectId() == null && input.projectCode() == null && input.projectName() == null)
            return client.query(context, input);
        var matches = projectReferences.resolve(context, input.projectId(), input.projectCode(), input.projectName());
        if (matches.size() > 1) {
            return new ClarificationPrompt("匹配到多个可见项目，请确认要查看哪个项目的工程产值（也可补充准确项目编号）：",
                    "PROJECT_REFERENCE", matches.stream().limit(5).map(item -> new ClarificationPrompt.Option(
                            "project:" + item.projectId(), item.projectName() + "（编号：" + item.projectCode() + "）",
                            ProjectOutputDomainContributor.CODE)).toList());
        }
        String resolvedId = matches.getFirst().projectId();
        var result = client.query(context, input.withProjectId(resolvedId));
        var filters = new java.util.ArrayList<>(result.appliedFilters());
        filters.add(java.util.Map.of("field", "projectId", "op", "eq", "value", resolvedId));
        return new com.smart.agent.tool.project.ProjectQueryResult(result.mode(), result.page(), result.pageSize(),
                result.total(), result.hasNext(), result.columns(), result.rows(), filters);
    }
}

