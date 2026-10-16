package com.smart.agent.tool.disbursement;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.ProjectReferenceResolver;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.OtherPaymentRequestDomainContributor;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class OtherPaymentRequestQueryTool implements AgentTool<OtherPaymentRequestQueryInput, Object> {
    private final OtherPaymentRequestBusinessClient client;
    private final ProjectReferenceResolver projectReferences;
    public OtherPaymentRequestQueryTool(OtherPaymentRequestBusinessClient client, ProjectReferenceResolver projectReferences) {
        this.client = client;
        this.projectReferences = projectReferences;
    }
    @Override public String key() { return "otherPaymentRequest.query"; }
    @Override public Class<OtherPaymentRequestQueryInput> inputType() { return OtherPaymentRequestQueryInput.class; }
    @Override public String requiredPermission() { return "menu:finance:other-payment-request"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    // Scope is checked against the canonical ID after resolving legacy code/name inputs, below.
    @Override public Optional<String> projectId(OtherPaymentRequestQueryInput input) { return Optional.empty(); }
    @Override public String description() {
        return "查询当前用户可见项目中审批完成的其他付款申请。detail为申请单列表，items为单据付款明细，summary为申请单数、明细条数、本次付款金额精确合计。"
                + "金额汇总子表本次付款金额，不得累加累计付款金额或分页明细；不含普通付款申请、报销及退款，不代表实际总付款。"
                + "支持申请单号registrationCode和单据真实ID requestId；日期仅支持createDate申请创建日期、projectCreateDate项目创建日期、paymentDate实际付款时间。"
                + "日期不明确调用otherPaymentRequest.clarifyDate，相对日期先取时间。待办、已办、流程仍使用approval工具。";
    }
    @Override public String argumentsSchemaJson() {
        return """
                {"type":"object","properties":{
                  "projectId":{"type":"string","description":"真实项目ID，不能填写项目编号。追问当前项目时继承已确认的真实项目ID"},
                  "projectCode":{"type":"string","description":"项目编号，例如20260708001；由程序在可见项目中精确定位"},
                  "projectName":{"type":"string","description":"默认精确项目名称筛选，同名多项时追问确认，不得默认包含匹配扩大范围"},
                  "projectNameMatch":{"type":"string","description":"仅允许exact或contains，默认exact。只有用户明确要求名称包含或模糊匹配时用contains，不能作为失败后的降级重试"},
                  "registrationCode":{"type":"string","description":"其他付款申请单号包含筛选"},
                  "requestId":{"type":"string","description":"其他付款申请列表返回的真实申请单ID；看某一单的付款明细时传入"},
                  "mode":{"type":"string","description":"detail申请单列表，items付款明细，summary申请单数、明细条数及本次付款金额全量合计"},
                  "dateField":{"type":"string","description":"createDate申请创建日期、projectCreateDate项目创建日期。日期不明确先追问，按付款时间筛选时仅计算区间内明细，单数按有匹配明细的申请去重"},
                  "startDate":{"type":"string","description":"开始日期 yyyy-MM-dd，含当天"},
                  "endDate":{"type":"string","description":"结束日期 yyyy-MM-dd，含当天"},
                  "page":{"type":"integer","description":"从1开始"},"pageSize":{"type":"integer","description":"1到100，默认20"}
                },"additionalProperties":false}
                """;
    }
    @Override public Object execute(OtherPaymentRequestQueryInput input, ToolContext context) {
        if ("contains".equals(input.projectNameMatch())
                || input.projectId() == null && input.projectCode() == null && input.projectName() == null)
            return client.query(context, input);
        var matches = projectReferences.resolve(context, input.projectId(), input.projectCode(), input.projectName());
        if (matches.size() > 1) {
            return new ClarificationPrompt("匹配到多个可见项目，请确认要查看哪个项目的其他付款申请（也可补充准确项目编号）：",
                    "PROJECT_REFERENCE", matches.stream().limit(5).map(item -> new ClarificationPrompt.Option(
                            "project:" + item.projectId(), item.projectName() + "（编号：" + item.projectCode() + "）",
                            OtherPaymentRequestDomainContributor.CODE)).toList());
        }
        String resolvedId = matches.getFirst().projectId();
        var result = client.query(context, input.withProjectId(resolvedId));
        var filters = new java.util.ArrayList<>(result.appliedFilters());
        filters.add(java.util.Map.of("field", "projectId", "op", "eq", "value", resolvedId));
        return new com.smart.agent.tool.project.ProjectQueryResult(result.mode(), result.page(), result.pageSize(),
                result.total(), result.hasNext(), result.columns(), result.rows(), filters);
    }
}
