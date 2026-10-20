package com.smart.agent.tool.material;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.ProjectReferenceResolver;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.MaterialRefundDomainContributor;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MaterialRefundQueryTool implements AgentTool<MaterialRefundQueryInput, Object> {
    private final MaterialRefundBusinessClient client;
    private final ProjectReferenceResolver projectReferences;
    public MaterialRefundQueryTool(MaterialRefundBusinessClient client, ProjectReferenceResolver projectReferences) {
        this.client = client;
        this.projectReferences = projectReferences;
    }
    @Override public String key() { return "materialRefund.query"; }
    @Override public Class<MaterialRefundQueryInput> inputType() { return MaterialRefundQueryInput.class; }
    @Override public String requiredPermission() { return "menu:material:refund"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    // Scope is checked against the canonical ID after resolving legacy code/name inputs, below.
    @Override public Optional<String> projectId(MaterialRefundQueryInput input) { return Optional.empty(); }
    @Override public String description() {
        return "查询可见项目中审批完成的材料退货退款（不含仅退货和公司仓）。detail单据列表，items按requestId查材料明细，summary全量单数、明细条数和refundAmount金额合计。"
                + "仅汇总本次明细金额，不能相加主单历史累计金额或当前页金额；退货退款金额不代表实际到账金额。"
                + "日期支持returnDate材料退货时间、createDate单据创建日期、projectCreateDate项目创建日期；含义不明确调用materialRefund.clarifyDate，相对日期先取时间。"
                + "支持单号registrationCode和真实单据ID requestId。每轮实时查询；待办、已办、流程仍用approval工具。";
    }
    @Override public String argumentsSchemaJson() {
        return """
                {"type":"object","properties":{
                  "projectId":{"type":"string","description":"真实项目ID，不能填写项目编号。追问当前项目时继承已确认的真实项目ID"},
                  "projectCode":{"type":"string","description":"项目编号，例如20260708001；由程序在可见项目中精确定位"},
                  "projectName":{"type":"string","description":"默认精确项目名称筛选，同名多项时追问确认，不得默认包含匹配扩大范围"},
                  "projectNameMatch":{"type":"string","description":"仅允许exact或contains，默认exact。只有用户明确要求名称包含或模糊匹配时用contains，不能作为失败后的降级重试"},
                  "registrationCode":{"type":"string","description":"材料退货退款单号包含筛选"},
                  "requestId":{"type":"string","description":"材料退货退款列表返回的真实申请单ID；看某一单的材料明细时传入"},
                  "mode":{"type":"string","description":"detail申请单列表，items材料明细，summary申请单数、明细条数及退货退款金额全量合计"},
                  "dateField":{"type":"string","description":"createDate申请创建日期、projectCreateDate项目创建日期。日期不明确先追问，returnDate退货时间。单数按当前范围有材料明细的退货退款单去重，金额仅为当前范围内退货退款小计"},
                  "startDate":{"type":"string","description":"开始日期 yyyy-MM-dd，含当天"},
                  "endDate":{"type":"string","description":"结束日期 yyyy-MM-dd，含当天"},
                  "page":{"type":"integer","description":"从1开始"},"pageSize":{"type":"integer","description":"1到100，默认20"}
                },"additionalProperties":false}
                """;
    }
    @Override public Object execute(MaterialRefundQueryInput input, ToolContext context) {
        if ("contains".equals(input.projectNameMatch())
                || input.projectId() == null && input.projectCode() == null && input.projectName() == null)
            return client.query(context, input);
        var matches = projectReferences.resolve(context, input.projectId(), input.projectCode(), input.projectName());
        if (matches.size() > 1) {
            return new ClarificationPrompt("匹配到多个可见项目，请确认要查看哪个项目的材料退货退款（也可补充准确项目编号）：",
                    "PROJECT_REFERENCE", matches.stream().limit(5).map(item -> new ClarificationPrompt.Option(
                            "project:" + item.projectId(), item.projectName() + "（编号：" + item.projectCode() + "）",
                            MaterialRefundDomainContributor.CODE)).toList());
        }
        String resolvedId = matches.getFirst().projectId();
        var result = client.query(context, input.withProjectId(resolvedId));
        var filters = new java.util.ArrayList<>(result.appliedFilters());
        filters.add(java.util.Map.of("field", "projectId", "op", "eq", "value", resolvedId));
        return new com.smart.agent.tool.project.ProjectQueryResult(result.mode(), result.page(), result.pageSize(),
                result.total(), result.hasNext(), result.columns(), result.rows(), filters);
    }
}
