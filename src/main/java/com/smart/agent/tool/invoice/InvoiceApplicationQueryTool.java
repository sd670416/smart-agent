package com.smart.agent.tool.invoice;

import com.smart.agent.tool.*;
import com.smart.agent.tool.project.ProjectReferenceResolver;
import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.InvoiceApplicationDomainContributor;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class InvoiceApplicationQueryTool implements AgentTool<InvoiceApplicationQueryInput, Object> {
    private final InvoiceApplicationBusinessClient client;
    private final ProjectReferenceResolver projectReferences;
    public InvoiceApplicationQueryTool(InvoiceApplicationBusinessClient client, ProjectReferenceResolver projectReferences) {
        this.client = client;
        this.projectReferences = projectReferences;
    }
    @Override public String key() { return "invoiceApplication.query"; }
    @Override public Class<InvoiceApplicationQueryInput> inputType() { return InvoiceApplicationQueryInput.class; }
    @Override public String requiredPermission() { return "menu:finance:invoice-application"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    // Scope is checked against the canonical ID after resolving legacy code/name inputs, below.
    @Override public Optional<String> projectId(InvoiceApplicationQueryInput input) { return Optional.empty(); }
    @Override public String description() {
        return "查询可见项目中审批完成的开票申请。detail分页主单列表或按requestId查看单据，items按requestId查看登记的发票明细，summary全量申请单数和开票申请金额合计。"
                + "汇总只取主单本次申请金额，不累加历史累计字段或发票明细；开票申请金额不等于实际已开票金额。"
                + "日期仅支持createDate申请创建日期、projectCreateDate项目创建日期；不明确先invoiceApplication.clarifyDate，相对日期先取当前时间。"
                + "每轮实时查询；待办、已办、流程仍使用approval。";
    }
    @Override public String argumentsSchemaJson() {
        return """
                {"type":"object","properties":{
                  "projectId":{"type":"string","description":"真实项目ID，不能填写项目编号。追问当前项目时继承已确认的真实项目ID"},
                  "projectCode":{"type":"string","description":"项目编号，例如20260708001；由程序在可见项目中精确定位"},
                  "projectName":{"type":"string","description":"默认精确项目名称筛选，同名多项时追问确认，不得默认包含匹配扩大范围"},
                  "projectNameMatch":{"type":"string","description":"仅允许exact或contains，默认exact。只有用户明确要求名称包含或模糊匹配时用contains，不能作为失败后的降级重试"},
                  "registrationCode":{"type":"string","description":"开票申请单号包含筛选"},
                  "requestId":{"type":"string","description":"列表返回的真实申请单ID；查看某单或登记发票明细时传入，不可填写序号或编号"},
                  "mode":{"type":"string","description":"detail申请单列表或单据详情，items登记发票明细（需requestId），summary申请数量及申请金额全量合计"},
                  "dateField":{"type":"string","description":"仅createDate申请创建日期、projectCreateDate项目创建日期。日期不明确先追问；暂不支持按发票开票日期筛选"},
                  "startDate":{"type":"string","description":"开始日期 yyyy-MM-dd，含当天"},
                  "endDate":{"type":"string","description":"结束日期 yyyy-MM-dd，含当天"},
                  "page":{"type":"integer","description":"从1开始"},"pageSize":{"type":"integer","description":"1到100，默认20"}
                },"additionalProperties":false}
                """;
    }
    @Override public Object execute(InvoiceApplicationQueryInput input, ToolContext context) {
        if ("contains".equals(input.projectNameMatch())
                || input.projectId() == null && input.projectCode() == null && input.projectName() == null)
            return client.query(context, input);
        var matches = projectReferences.resolve(context, input.projectId(), input.projectCode(), input.projectName());
        if (matches.size() > 1) {
            return new ClarificationPrompt("匹配到多个可见项目，请确认要查看哪个项目的开票申请（也可补充准确项目编号）：",
                    "PROJECT_REFERENCE", matches.stream().limit(5).map(item -> new ClarificationPrompt.Option(
                            "project:" + item.projectId(), item.projectName() + "（编号：" + item.projectCode() + "）",
                            InvoiceApplicationDomainContributor.CODE)).toList());
        }
        String resolvedId = matches.getFirst().projectId();
        var result = client.query(context, input.withProjectId(resolvedId));
        var filters = new java.util.ArrayList<>(result.appliedFilters());
        filters.add(java.util.Map.of("field", "projectId", "op", "eq", "value", resolvedId));
        return new com.smart.agent.tool.project.ProjectQueryResult(result.mode(), result.page(), result.pageSize(),
                result.total(), result.hasNext(), result.columns(), result.rows(), filters);
    }
}
