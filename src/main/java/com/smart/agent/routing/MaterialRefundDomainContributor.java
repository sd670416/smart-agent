package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MaterialRefundDomainContributor implements BusinessDomainContributor {
    public static final String CODE = "MATERIAL_REFUND";
    public static final String PERMISSION = "menu:material:refund";
    private static final List<String> TERMS = List.of("材料退货退款", "材料退款", "退货退款");
    private static final BusinessDomainDescriptor DESCRIPTOR = new BusinessDomainDescriptor(
            CODE, "材料退货退款", List.of("materialRefund.query", "materialRefund.clarifyDate", "materialRefund.compare"),
            List.of(new DataScopeDescriptor("MATERIAL_MENU", "材料退货菜单及可见项目范围", List.of("MENU", "PROJECT_MEMBER"))),
            "REALTIME", TERMS);
    public static boolean matches(String question) {
        return question != null && TERMS.stream().anyMatch(question::contains);
    }
    @Override public BusinessDomainDescriptor descriptor() { return DESCRIPTOR; }
    @Override public boolean availableTo(AgentUserContext context) {
        return context != null && context.permissions().contains(PERMISSION);
    }
}
