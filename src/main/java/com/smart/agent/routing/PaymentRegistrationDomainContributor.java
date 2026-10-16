package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PaymentRegistrationDomainContributor implements BusinessDomainContributor {
    public static final String CODE = "PAYMENT_REGISTRATION";
    public static final String PERMISSION = "menu:finance:payment-registration";
    private static final List<String> TERMS = List.of("回款登记", "回款登记单");
    private static final BusinessDomainDescriptor DESCRIPTOR = new BusinessDomainDescriptor(
            CODE, "回款登记", List.of("paymentRegistration.query", "paymentRegistration.clarifyDate"),
            List.of(new DataScopeDescriptor("FINANCE_MENU", "回款登记菜单及可见项目范围", List.of("MENU", "PROJECT_MEMBER"))),
            "REALTIME", TERMS);
    public static boolean matches(String question) {
        return question != null && TERMS.stream().anyMatch(question::contains);
    }
    @Override public BusinessDomainDescriptor descriptor() { return DESCRIPTOR; }
    @Override public boolean availableTo(AgentUserContext context) {
        return context != null && context.permissions().contains(PERMISSION);
    }
}
