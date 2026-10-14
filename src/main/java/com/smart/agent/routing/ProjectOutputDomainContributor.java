package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProjectOutputDomainContributor implements BusinessDomainContributor {
    public static final String CODE = "PROJECT_OUTPUT";
    private static final List<String> TERMS = List.of("工程产值", "工程产值单", "累计结算金额");
    private static final BusinessDomainDescriptor DESCRIPTOR = new BusinessDomainDescriptor(
            CODE, "工程产值", List.of("projectOutput.query", "projectOutput.clarifyDate"),
            List.of(new DataScopeDescriptor("PROJECT_MENU", "项目菜单及可见项目范围", List.of("MENU", "PROJECT_MEMBER"))),
            "REALTIME", TERMS);

    public static boolean matches(String question) {
        return question != null && !isOtherOutput(question) && TERMS.stream().anyMatch(question::contains);
    }

    public static boolean isOtherOutput(String question) {
        return question != null && question.matches(".*(劳务|专包|专业分包).*产值.*");
    }

    public static boolean isAmbiguousOutput(String question) {
        return question != null && question.contains("产值") && !matches(question) && !isOtherOutput(question);
    }

    public static boolean isWorkflowQuery(String question) {
        return question != null && question.matches(".*(待办|已办|我发起|审批流|流程|办理记录|审批节点|办理人|处理人).*" );
    }

    @Override public BusinessDomainDescriptor descriptor() { return DESCRIPTOR; }
    @Override public boolean availableTo(AgentUserContext context) {
        return context != null && context.permissions().contains("menu:project");
    }
}

