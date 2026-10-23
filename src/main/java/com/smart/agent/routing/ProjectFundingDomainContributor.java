package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ProjectFundingDomainContributor implements BusinessDomainContributor {
    public static final String CODE="PROJECT_FUNDING";
    private static final List<String> TERMS=List.of("累计付款","总付款","净付款","垫资");
    public static Set<String> permissions(String metric) {
        if(!Set.of("cumulative","net","funding").contains(metric))throw new IllegalArgumentException("Unsupported metric");
        Set<String> permissions=new LinkedHashSet<>(Set.of("menu:finance:payment-request",
                "menu:finance:other-payment-request","menu:finance:project-reimburse"));
        if(!"cumulative".equals(metric))permissions.add("menu:material:refund");
        if("funding".equals(metric))permissions.addAll(Set.of("menu:finance:payment-registration","menu:finance:other-income-registration"));
        return Collections.unmodifiableSet(permissions);
    }
    public static boolean matches(String question){return question!=null && TERMS.stream().anyMatch(question::contains);}
    public static String metric(String question) {
        return question!=null && question.contains("垫资")?"funding":question!=null && question.contains("净付款")?"net":"cumulative";
    }
    @Override public BusinessDomainDescriptor descriptor(){return new BusinessDomainDescriptor(CODE,"总付款与项目垫资",
            List.of("projectFunding.query","projectFunding.compare","projectFunding.clarifyDate"),List.of(new DataScopeDescriptor("FINANCE_MENUS","对应来源菜单及可见项目范围",List.of("MENU","PROJECT_MEMBER"))),"REALTIME",TERMS);}
    @Override public boolean availableTo(AgentUserContext context) {
        return context!=null && context.permissions().containsAll(permissions("cumulative"));
    }
}
