package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestDomainContributor implements BusinessDomainContributor {
    public static final String CODE = "PAYMENT_REQUEST";
    public static final String PERMISSION = "menu:finance:payment-request";
    private static final List<String> TERMS = List.of("付款申请");
    private static final BusinessDomainDescriptor DESCRIPTOR = new BusinessDomainDescriptor(
            CODE, "付款申请", List.of("paymentRequest.query", "paymentRequest.clarifyDate"),
            List.of(new DataScopeDescriptor("FINANCE_MENU", "付款申请菜单及可见项目范围", List.of("MENU", "PROJECT_MEMBER"))),
            "REALTIME", TERMS);
    public static boolean matches(String question) {
        return question != null && TERMS.stream().anyMatch(question::contains)
                && !question.contains("其他付款") && !OtherPaymentRequestDomainContributor.matches(question);
    }
    @Override public BusinessDomainDescriptor descriptor() { return DESCRIPTOR; }
    @Override public boolean availableTo(AgentUserContext context) {
        return context != null && context.permissions().contains(PERMISSION);
    }
}
