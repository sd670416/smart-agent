package com.smart.agent.tool.operation;

import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.tool.*;
import com.smart.agent.tool.project.ProjectReferenceResolver;
import org.springframework.stereotype.Component;

@Component
public class ProjectOperationQueryTool implements AgentTool<ProjectOperationQueryInput, Object> {
    private final ProjectOperationBusinessClient client;
    private final ProjectReferenceResolver projects;
    public ProjectOperationQueryTool(ProjectOperationBusinessClient client, ProjectReferenceResolver projects) {
        this.client = client; this.projects = projects;
    }
    @Override public String key() { return "projectOperation.query"; }
    @Override public Class<ProjectOperationQueryInput> inputType() { return ProjectOperationQueryInput.class; }
    @Override public String requiredPermission() { return "menu:project"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() {
        return "实时查询可见项目的审批完成产值及甲方回款分析：完成产值、上报甲方产值、系统确认产值是同一本期产值合计；"
                + "甲方回款仅回款登记，其他收入单列。合同回款比例=回款/最终合同额；产值回款比例=回款/产值；未收余额=产值-回款。"
                + "summary全量合计并重算比例，detail按项目分页，evidence返回来源单据编号及真实标识，不是单据完整详情。"
                + "当前仅支持CURRENT当前累计；期间或历史模式会明确返回不支持，不得改查当前数据代替。"
                + "比例值已乘100，null须按对应Reason说明，不能变0；每轮重新查询，不从历史结果推算。";
    }
    @Override public String argumentsSchemaJson() {
        return """
                {"type":"object","properties":{
                  "projectId":{"type":"string","description":"真实项目标识，不能填写列表序号"},
                  "projectCode":{"type":"string","description":"精确项目编号"},
                  "projectName":{"type":"string","description":"精确项目名称，同名先确认，不自动模糊扩大范围"},
                  "organizationId":{"type":"string","description":"已确认的真实组织标识，不填组织名称"},
                  "projectNature":{"type":"string","description":"已确认的项目性质字典值，不猜测编码"},
                  "managerId":{"type":"string","description":"已确认的项目经理用户标识，不填姓名或猜测账号"},
                  "firstPartyId":{"type":"string","description":"已确认的施工合同甲方单位真实标识，用于选择项目"},
                  "mode":{"type":"string","description":"detail项目列表，summary全量汇总，evidence来源单据列表；默认detail"},
                  "dateMode":{"type":"string","description":"默认CURRENT当前累计；PERIOD期间、AS_OF历史截至日当前未支持，不得静默忽略用户日期"},
                  "dateField":{"type":"string","description":"期间或截至日的明确日期维度；CURRENT禁止携带日期字段"},
                  "startDate":{"type":"string","description":"yyyy-MM-dd，PERIOD须有起止日期"},
                  "endDate":{"type":"string","description":"yyyy-MM-dd，AS_OF仅结束日期"},
                  "page":{"type":"integer","description":"从1开始"},
                  "pageSize":{"type":"integer","description":"1至100，默认20"}
                },"additionalProperties":false}
                """;
    }
    @Override public Object execute(ProjectOperationQueryInput input, ToolContext context) {
        if (input.projectId() == null && input.projectCode() == null && input.projectName() == null) return client.query(context, input);
        var matches = projects.resolve(context, input.projectId(), input.projectCode(), input.projectName());
        if (matches.size() > 1) return new ClarificationPrompt("找到多个同名项目，请确认要查看哪个项目的产值与回款：", "PROJECT_REFERENCE",
                matches.stream().limit(5).map(p -> new ClarificationPrompt.Option("project:" + p.projectId(),
                        p.projectName() + "（编号：" + p.projectCode() + "）", "PROJECT_OPERATION")).toList());
        String id = matches.getFirst().projectId();
        var result = client.query(context, input.withProjectId(id));
        var filters = new java.util.ArrayList<>(result.appliedFilters());
        filters.add(java.util.Map.of("field", "projectId", "op", "eq", "value", id));
        return new com.smart.agent.tool.project.ProjectQueryResult(result.mode(), result.page(), result.pageSize(),
                result.total(), result.hasNext(), result.columns(), result.rows(), filters);
    }
}
