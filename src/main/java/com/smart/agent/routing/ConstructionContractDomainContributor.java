package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ConstructionContractDomainContributor implements BusinessDomainContributor {
    public static final String CODE = "CONSTRUCTION_CONTRACT";
    private static final List<String> TERMS = List.of("施工合同", "建设合同", "各类型项目合同金额", "项目类型合同金额");
    private static final BusinessDomainDescriptor DESCRIPTOR = new BusinessDomainDescriptor(
            CODE, "施工合同", List.of("constructionContract.query", "constructionContract.clarifyDate", "constructionContract.compare"),
            List.of(new DataScopeDescriptor("PROJECT_MENU", "项目菜单及可见项目范围", List.of("MENU", "PROJECT_MEMBER"))),
            "REALTIME", TERMS);

    public static boolean matches(String question) {
        return question != null && TERMS.stream().anyMatch(question::contains);
    }

    public static boolean isWorkflowQuery(String question) {
        return question != null && question.matches(".*(待办|已办|我发起|审批流|流程|办理记录|审批节点|办理人|处理人|谁在审批|超时审批).*" );
    }

    @Override public BusinessDomainDescriptor descriptor() { return DESCRIPTOR; }
    @Override public boolean availableTo(AgentUserContext context) {
        return context != null && context.permissions().contains("menu:project");
    }
}
