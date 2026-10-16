package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ProjectReceiptsDomainContributor implements BusinessDomainContributor {
    public static final String CODE = "PROJECT_RECEIPTS";
    private static final List<String> TERMS = List.of("已收项目款", "项目已收款", "项目回款总额", "已收款金额", "项目已收金额");
    public static boolean matches(String question) {
        if (question == null) return false;
        boolean payment = PaymentRegistrationDomainContributor.matches(question);
        boolean otherIncome = OtherIncomeRegistrationDomainContributor.matches(question);
        if (payment || otherIncome) {
            return payment && otherIncome && question.matches(".*(和|与|及|加|\\+|、).*")
                    && question.matches(".*(合计|汇总|总额|总金额).*");
        }
        return TERMS.stream().anyMatch(question::contains);
    }
    public static boolean canAccess(Set<String> permissions) {
        return permissions != null && permissions.contains("menu:finance:payment-registration")
                && permissions.contains("menu:finance:other-income-registration");
    }
    @Override public boolean availableTo(AgentUserContext context) {
        return context != null && canAccess(context.permissions());
    }
    @Override public BusinessDomainDescriptor descriptor() {
        return new BusinessDomainDescriptor(CODE, "已收项目款", List.of("projectReceipts.query", "projectReceipts.compare", "projectReceipts.clarifyDate"),
                List.of(new DataScopeDescriptor("FINANCE_MENUS", "两类回款菜单及可见项目范围", List.of("MENU", "PROJECT_MEMBER"))),
                "REALTIME", TERMS);
    }
}
