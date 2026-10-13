package com.smart.agent.tool.contract;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.ProjectReferenceResolver;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.ConstructionContractDomainContributor;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ConstructionContractQueryTool implements AgentTool<ConstructionContractQueryInput, Object> {
    private final ConstructionContractBusinessClient client;
    private final ProjectReferenceResolver projectReferences;
    public ConstructionContractQueryTool(ConstructionContractBusinessClient client, ProjectReferenceResolver projectReferences) {
        this.client = client;
        this.projectReferences = projectReferences;
    }
    @Override public String key() { return "constructionContract.query"; }
    @Override public Class<ConstructionContractQueryInput> inputType() { return ConstructionContractQueryInput.class; }
    @Override public String requiredPermission() { return "menu:project"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    // Scope is checked against the canonical ID after resolving legacy code/name inputs, below.
    @Override public Optional<String> projectId(ConstructionContractQueryInput input) { return Optional.empty(); }
    @Override public String description() {
        return "查询当前用户可见项目的审批完成登记施工合同：明细、数量及建设合同总金额（最终合同金额合计）。"
                + "不查询待办或其他种类合同。日期维度不明确时先调用 constructionContract.clarifyDate；"
                + "原经营看板指标日期是项目创建日期，明确签订日期时用 signingDate。数量或金额合计用 summary，不对分页明细自行求总额。";
    }
    @Override public String argumentsSchemaJson() {
        return """
                {"type":"object","properties":{
                  "projectId":{"type":"string","description":"真实项目ID，不能填写项目编号。追问当前项目时继承已确认的真实项目ID"},
                  "projectCode":{"type":"string","description":"项目编号，例如20260708001；由程序在可见项目中精确定位"},
                  "projectName":{"type":"string","description":"默认精确项目名称筛选，同名多项时追问确认，不得默认包含匹配扩大范围"},
                  "projectNameMatch":{"type":"string","description":"仅允许exact或contains，默认exact。只有用户明确要求名称包含或模糊匹配时用contains，不能作为失败后的降级重试"},
                  "contractName":{"type":"string","description":"合同名称包含筛选"},
                  "mode":{"type":"string","description":"只允许 detail 或 summary：detail 返回分页明细；summary 返回合同数量及最终金额总计"},
                  "dateField":{"type":"string","description":"只允许 projectCreateDate（项目创建日期）、signingDate（合同签订日期）、createDate（合同创建日期）。日期不明确先追问"},
                  "startDate":{"type":"string","description":"开始日期 yyyy-MM-dd，含当天"},
                  "endDate":{"type":"string","description":"结束日期 yyyy-MM-dd，含当天"},
                  "page":{"type":"integer","description":"从1开始"},"pageSize":{"type":"integer","description":"1到100，默认20"}
                },"additionalProperties":false}
                """;
    }
    @Override public Object execute(ConstructionContractQueryInput input, ToolContext context) {
        if ("contains".equals(input.projectNameMatch())
                || input.projectId() == null && input.projectCode() == null && input.projectName() == null)
            return client.query(context, input);
        var matches = projectReferences.resolve(context, input.projectId(), input.projectCode(), input.projectName());
        if (matches.size() > 1) {
            return new ClarificationPrompt("匹配到多个可见项目，请确认要查看哪个项目的施工合同（也可补充准确项目编号）：",
                    "PROJECT_REFERENCE", matches.stream().limit(5).map(item -> new ClarificationPrompt.Option(
                            "project:" + item.projectId(), item.projectName() + "（编号：" + item.projectCode() + "）",
                            ConstructionContractDomainContributor.CODE)).toList());
        }
        String resolvedId = matches.getFirst().projectId();
        var result = client.query(context, input.withProjectId(resolvedId));
        var filters = new java.util.ArrayList<>(result.appliedFilters());
        filters.add(java.util.Map.of("field", "projectId", "op", "eq", "value", resolvedId));
        return new com.smart.agent.tool.project.ProjectQueryResult(result.mode(), result.page(), result.pageSize(),
                result.total(), result.hasNext(), result.columns(), result.rows(), filters);
    }
}
