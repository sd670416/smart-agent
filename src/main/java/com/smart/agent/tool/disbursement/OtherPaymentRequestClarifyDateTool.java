package com.smart.agent.tool.disbursement;

import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.OtherPaymentRequestDomainContributor;
import com.smart.agent.tool.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OtherPaymentRequestClarifyDateTool implements AgentTool<OtherPaymentRequestClarifyDateTool.Input, ClarificationPrompt> {
    public record Input() {}
    @Override public String key() { return "otherPaymentRequest.clarifyDate"; }
    @Override public Class<Input> inputType() { return Input.class; }
    @Override public String requiredPermission() { return "menu:finance:other-payment-request"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() { return "其他付款申请查询的日期含义不明确时，提供申请创建日期、项目创建日期、实际付款时间供用户确认；不读取业务数据。"; }
    @Override public ClarificationPrompt execute(Input input, ToolContext context) {
        String domain = OtherPaymentRequestDomainContributor.CODE;
        return new ClarificationPrompt("您希望按哪个日期筛选其他付款申请？", "DATE_FIELD", List.of(
                new ClarificationPrompt.Option("date:projectCreateDate", "项目创建日期", domain),
                new ClarificationPrompt.Option("date:createDate", "申请创建日期", domain),
                new ClarificationPrompt.Option("date:paymentDate", "实际付款时间", domain)));
    }
}
