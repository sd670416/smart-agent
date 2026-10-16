package com.smart.agent.tool.disbursement;

import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.routing.PaymentRequestDomainContributor;
import com.smart.agent.tool.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestClarifyDateTool implements AgentTool<PaymentRequestClarifyDateTool.Input, ClarificationPrompt> {
    public record Input() {}
    @Override public String key() { return "paymentRequest.clarifyDate"; }
    @Override public Class<Input> inputType() { return Input.class; }
    @Override public String requiredPermission() { return "menu:finance:payment-request"; }
    @Override public ToolRisk risk() { return ToolRisk.L1; }
    @Override public String description() { return "付款申请查询的日期含义不明确时，提供申请创建日期、项目创建日期供用户确认；不读取业务数据。"; }
    @Override public ClarificationPrompt execute(Input input, ToolContext context) {
        String domain = PaymentRequestDomainContributor.CODE;
        return new ClarificationPrompt("您希望按哪个日期筛选付款申请？", "DATE_FIELD", List.of(
                new ClarificationPrompt.Option("date:projectCreateDate", "项目创建日期", domain),
                new ClarificationPrompt.Option("date:createDate", "申请创建日期", domain)));
    }
}
