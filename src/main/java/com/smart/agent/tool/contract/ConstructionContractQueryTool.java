package com.smart.agent.tool.contract;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.ProjectQueryResult;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ConstructionContractQueryTool implements AgentTool<ConstructionContractQueryInput, ProjectQueryResult> {
    private final ConstructionContractBusinessClient client;
    public ConstructionContractQueryTool(ConstructionContractBusinessClient client) { this.client = client; }
    @Override public String key() { return "constructionContract.query"; }
    @Override public Class<ConstructionContractQueryInput> inputType() { return ConstructionContractQueryInput.class; }
    @Override public String requiredPermission() { return "menu:project"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public Optional<String> projectId(ConstructionContractQueryInput input) { return Optional.ofNullable(input.projectId()); }
    @Override public String description() {
        return "查询当前用户可见项目的审批完成登记施工合同：明细、数量及建设合同总金额（最终合同金额合计）。"
                + "不查询待办或其他种类合同。日期维度不明确时先调用 constructionContract.clarifyDate；"
                + "原经营看板指标日期是项目创建日期，明确签订日期时用 signingDate。数量或金额合计用 summary，不对分页明细自行求总额。";
    }
    @Override public String argumentsSchemaJson() {
        return """
                {"type":"object","properties":{
                  "projectId":{"type":"string","description":"指定项目ID，仅可使用可信可见项目"},
                  "projectName":{"type":"string","description":"项目名称包含筛选"},
                  "contractName":{"type":"string","description":"合同名称包含筛选"},
                  "mode":{"type":"string","description":"只允许 detail 或 summary：detail 返回分页明细；summary 返回合同数量及最终金额总计"},
                  "dateField":{"type":"string","description":"只允许 projectCreateDate（项目创建日期）、signingDate（合同签订日期）、createDate（合同创建日期）。日期不明确先追问"},
                  "startDate":{"type":"string","description":"开始日期 yyyy-MM-dd，含当天"},
                  "endDate":{"type":"string","description":"结束日期 yyyy-MM-dd，含当天"},
                  "page":{"type":"integer","description":"从1开始"},"pageSize":{"type":"integer","description":"1到100，默认20"}
                },"additionalProperties":false}
                """;
    }
    @Override public ProjectQueryResult execute(ConstructionContractQueryInput input, ToolContext context) {
        return client.query(context, input);
    }
}
