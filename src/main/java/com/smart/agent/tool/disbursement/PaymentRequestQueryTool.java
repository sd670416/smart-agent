package com.smart.agent.tool.disbursement;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.ProjectReferenceResolver;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.PaymentRequestDomainContributor;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestQueryTool implements AgentTool<PaymentRequestQueryInput, Object> {
    private final PaymentRequestBusinessClient client;
    private final ProjectReferenceResolver projectReferences;
    public PaymentRequestQueryTool(PaymentRequestBusinessClient client, ProjectReferenceResolver projectReferences) {
        this.client = client;
        this.projectReferences = projectReferences;
    }
    @Override public String key() { return "paymentRequest.query"; }
    @Override public Class<PaymentRequestQueryInput> inputType() { return PaymentRequestQueryInput.class; }
    @Override public String requiredPermission() { return "menu:finance:payment-request"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    // Scope is checked against the canonical ID after resolving legacy code/name inputs, below.
    @Override public Optional<String> projectId(PaymentRequestQueryInput input) { return Optional.empty(); }
    @Override public String description() {
        return "查询当前用户可见项目中审批完成的付款申请。detail无requestId为列表，带真实requestId为单据自身详情；items为付款明细，summary为全量精确合计。"
                + "金额汇总子表本次付款金额，不得累加累计付款金额或分页明细；不含其他付款申请、报销及退款，不代表实际总付款。"
                + "支持申请单号registrationCode和主单真实ID requestId，不能用明细itemId替代。明细翻页保留主单及筛选；返回主单用detail和同一requestId。"
                + "主单详情含所属组织、项目经理、项目性质和联营单位；明细含当前合同金额及登记时保存的累计值，银行账户和附件未接入。"
                + "日期仅支持createDate申请创建日期、projectCreateDate项目创建日期。"
                + "日期不明确调用paymentRequest.clarifyDate，相对日期先取时间。待办、已办、流程仍使用approval工具。";
    }
    @Override public String argumentsSchemaJson() {
        return """
                {"type":"object","properties":{
                  "projectId":{"type":"string","description":"真实项目ID，不能填写项目编号。追问当前项目时继承已确认的真实项目ID"},
                  "projectCode":{"type":"string","description":"项目编号，例如20260708001；由程序在可见项目中精确定位"},
                  "projectName":{"type":"string","description":"默认精确项目名称筛选，同名多项时追问确认，不得默认包含匹配扩大范围"},
                  "projectNameMatch":{"type":"string","description":"仅允许exact或contains，默认exact。只有用户明确要求名称包含或模糊匹配时用contains，不能作为失败后的降级重试"},
                  "registrationCode":{"type":"string","description":"付款申请单号包含筛选"},
                  "requestId":{"type":"string","description":"列表返回的真实主单ID，非itemId；查单据详情或该单明细传入。翻页、返回主单保留；改查全列表或summary须清除"},
                  "mode":{"type":"string","description":"detail无requestId为列表、有requestId为自身详情；items付款明细；summary全量合计不能携带requestId"},
                  "dateField":{"type":"string","description":"createDate申请创建日期、projectCreateDate项目创建日期。日期不明确先追问，付款申请没有独立的到账日期"},
                  "startDate":{"type":"string","description":"开始日期 yyyy-MM-dd，含当天"},
                  "endDate":{"type":"string","description":"结束日期 yyyy-MM-dd，含当天"},
                  "page":{"type":"integer","description":"从1开始"},"pageSize":{"type":"integer","description":"1到100，默认20"}
                },"additionalProperties":false}
                """;
    }
    @Override public Object execute(PaymentRequestQueryInput input, ToolContext context) {
        if ("contains".equals(input.projectNameMatch())
                || input.projectId() == null && input.projectCode() == null && input.projectName() == null)
            return client.query(context, input);
        var matches = projectReferences.resolve(context, input.projectId(), input.projectCode(), input.projectName());
        if (matches.size() > 1) {
            return new ClarificationPrompt("匹配到多个可见项目，请确认要查看哪个项目的付款申请（也可补充准确项目编号）：",
                    "PROJECT_REFERENCE", matches.stream().limit(5).map(item -> new ClarificationPrompt.Option(
                            "project:" + item.projectId(), item.projectName() + "（编号：" + item.projectCode() + "）",
                            PaymentRequestDomainContributor.CODE)).toList());
        }
        String resolvedId = matches.getFirst().projectId();
        var result = client.query(context, input.withProjectId(resolvedId));
        var filters = new java.util.ArrayList<>(result.appliedFilters());
        filters.add(java.util.Map.of("field", "projectId", "op", "eq", "value", resolvedId));
        return new com.smart.agent.tool.project.ProjectQueryResult(result.mode(), result.page(), result.pageSize(),
                result.total(), result.hasNext(), result.columns(), result.rows(), filters);
    }
}
