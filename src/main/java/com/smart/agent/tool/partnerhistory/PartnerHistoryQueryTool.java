package com.smart.agent.tool.partnerhistory;

import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.tool.*;
import com.smart.agent.tool.project.ProjectReferenceResolver;
import com.smart.agent.tool.project.ProjectQueryResult;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PartnerHistoryQueryTool implements AgentTool<PartnerHistoryQueryInput, Object> {
    private static final String DOMAIN = "PARTNER_HISTORY";
    private final PartnerHistoryBusinessClient client;
    private final ProjectReferenceResolver projects;
    public PartnerHistoryQueryTool(PartnerHistoryBusinessClient client, ProjectReferenceResolver projects) {
        this.client = client; this.projects = projects;
    }
    @Override public String key() { return "partnerHistory.query"; }
    @Override public Class<PartnerHistoryQueryInput> inputType() { return PartnerHistoryQueryInput.class; }
    @Override public String requiredPermission() { return ""; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() {
        return "实时查询指定甲方或供应商在有权查看项目中的合作历史，合同、产值、回款、结算、付款与票据明细及全量合计。"
                + "只计审批完成业务。甲方回款只含对应施工合同的回款登记，不含其他收入；开票列是审批完成开票申请金额。"
                + "供应商覆盖材料、劳务、机械、专包四类合同，材料账款已作退货退款净额调整，不得二次扣减。"
                + "签约日期只选择合同，返回这些合同当前累计账款，不是期间资金发生额。没有日期默认当前累计。"
                + "姓名精确匹配，同名先确认真实合作方ID；类型不明确先确认甲方还是供应商；不以项目或列表序号代替合作方ID。"
                + "summary是全量合计，不能对分页列表自己求总数；缺来源权限或数据的null显示原因，不当作0。"
                + "按columns中文标签用Markdown表格展示，metricStatuses仅辅助解释，不能输出内部字段名/权限编码/SQL。"
                + "详情仅返回实际业务单据定位及金额，不代替审批表单，也不办理或修改记录。";
    }
    @Override public String argumentsSchemaJson() {
        return """
                {"type":"object","properties":{
                  "partnerType":{"type":"string","description":"CUSTOMER甲方或SUPPLIER供应商；未明确先追问"},
                  "partnerId":{"type":"string","description":"已确认的真实合作方ID；使用ID后不再传partnerName"},
                  "partnerName":{"type":"string","description":"精确合作方名称，同名必须先选具体单位"},
                  "contractType":{"type":"string","description":"仅供应商：1材料、2劳务、3机械、4专包；不填查四类，需四类合同权限；甲方不填"},
                  "contractId":{"type":"string","description":"真实合同ID，用于查看所选合同，不是列表序号"},
                  "recordId":{"type":"string","description":"真实结算/付款/票据主单ID，只用于对应明细mode，不是合同ID"},
                  "projectId":{"type":"string","description":"真实项目ID"},
                  "projectCode":{"type":"string","description":"精确项目编号"},
                  "projectName":{"type":"string","description":"精确项目名称，同名先确认"},
                  "projectNature":{"type":"string","description":"项目性质字典值，必须来自已确认字段目录，不猜编码"},
                  "managerId":{"type":"string","description":"真实项目经理用户ID，不填写姓名"},
                  "organizationId":{"type":"string","description":"真实所属组织ID"},
                  "mode":{"type":"string","description":"partners可选合作方；detail或contracts合同列表；summary全量账款合计；settlements供应商结算单；payments甲方回款或供应商付款单；invoices甲方开票申请或供应商收票登记"},
                  "dateField":{"type":"string","description":"仅支持contractSigningDate合同签约日期；不能代替资金发生时间"},
                  "startDate":{"type":"string","description":"签约开始日期yyyy-MM-dd，含当天"},
                  "endDate":{"type":"string","description":"签约结束日期yyyy-MM-dd，含当天"},
                  "page":{"type":"integer","description":"从1开始，默认1"},
                  "pageSize":{"type":"integer","description":"1至100，默认20"}
                },"additionalProperties":false}
                """;
    }
    @Override public Object execute(PartnerHistoryQueryInput input, ToolContext context) {
        if (!Set.of("CUSTOMER", "SUPPLIER").contains(input.partnerType() == null ? "" : input.partnerType()))
            return new ClarificationPrompt("请确认要查询哪类合作方的历史：", "PARTNER_TYPE", List.of(
                    new ClarificationPrompt.Option("partnerType:CUSTOMER", "甲方合作历史", DOMAIN),
                    new ClarificationPrompt.Option("partnerType:SUPPLIER", "供应商合作历史", DOMAIN)));
        PartnerHistoryQueryInput resolved = input;
        if (input.projectId() != null || input.projectCode() != null || input.projectName() != null) {
            var matches = projects.resolve(context, input.projectId(), input.projectCode(), input.projectName());
            if (matches.size() > 1) return new ClarificationPrompt("匹配到多个项目，请确认要查询哪一个项目的合作历史：", "PROJECT_REFERENCE",
                    matches.stream().limit(5).map(p -> new ClarificationPrompt.Option("project:" + p.projectId(),
                            p.projectName() + "（编号：" + p.projectCode() + "）", DOMAIN)).toList());
            resolved = input.withProjectId(matches.getFirst().projectId());
        }
        if (!"partners".equals(resolved.mode()) && resolved.partnerId() == null && resolved.partnerName() != null) {
            ProjectQueryResult candidates = client.query(context, resolved.lookup());
            if (candidates.total() != null && candidates.total() > 1) {
                return new ClarificationPrompt("存在同名合作单位，请根据编号确认具体单位：", "PARTNER_REFERENCE",
                        candidates.rows().stream().limit(5).map(p -> new ClarificationPrompt.Option("partner:" + p.get("partnerId"),
                                p.get("partnerName") + "（编号：" + p.get("partnerCode") + "）", DOMAIN)).toList());
            }
            if (candidates.rows().isEmpty()) return candidates;
            resolved = resolved.withPartner(String.valueOf(candidates.rows().getFirst().get("partnerId")));
        }
        return client.query(context, resolved);
    }
}
