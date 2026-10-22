package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OtherPaymentRequestDomainContributor implements BusinessDomainContributor {
    public static final String CODE = "OTHER_PAYMENT_REQUEST";
    public static final String PERMISSION = "menu:finance:other-payment-request";
    private static final List<String> TERMS = List.of("其他付款申请", "其他费用付款", "其它付款申请", "其它费用付款");
    private static final BusinessDomainDescriptor DESCRIPTOR = new BusinessDomainDescriptor(
            CODE, "其他付款申请", List.of("otherPaymentRequest.query", "otherPaymentRequest.clarifyDate", "otherPaymentRequest.compare"),
            List.of(new DataScopeDescriptor("FINANCE_MENU", "其他付款申请菜单及可见项目范围", List.of("MENU", "PROJECT_MEMBER"))),
            "REALTIME", TERMS);
    public static boolean matches(String question) {
        return question != null && TERMS.stream().anyMatch(question::contains);
    }
    @Override public BusinessDomainDescriptor descriptor() { return DESCRIPTOR; }
    @Override public boolean availableTo(AgentUserContext context) {
        return context != null && context.permissions().contains(PERMISSION);
    }
}
