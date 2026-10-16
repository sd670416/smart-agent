package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProjectReimburseDomainContributor implements BusinessDomainContributor {
    public static final String CODE = "PROJECT_REIMBURSE";
    public static final String PERMISSION = "menu:finance:project-reimburse";
    private static final List<String> TERMS = List.of("项目报销", "工程报销");
    private static final BusinessDomainDescriptor DESCRIPTOR = new BusinessDomainDescriptor(
            CODE, "项目报销", List.of("projectReimburse.query", "projectReimburse.clarifyDate"),
            List.of(new DataScopeDescriptor("FINANCE_MENU", "项目报销菜单及可见项目范围", List.of("MENU", "PROJECT_MEMBER"))),
            "REALTIME", TERMS);
    public static boolean matches(String question) {
        return question != null && TERMS.stream().anyMatch(question::contains);
    }
    @Override public BusinessDomainDescriptor descriptor() { return DESCRIPTOR; }
    @Override public boolean availableTo(AgentUserContext context) {
        return context != null && context.permissions().contains(PERMISSION);
    }
}
