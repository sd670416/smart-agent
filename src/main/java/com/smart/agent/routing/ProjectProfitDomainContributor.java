package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ProjectProfitDomainContributor implements BusinessDomainContributor {
    public static final String CODE="PROJECT_PROFIT";
    private static final List<String> TERMS=List.of("利润","毛利");
    public static Set<String> permissions(String metric){
        if(!Set.of("budget","actual","both").contains(metric))throw new IllegalArgumentException("Unsupported profit metric");
        Set<String> result=new LinkedHashSet<>();
        if(!"actual".equals(metric))result.addAll(Set.of("menu:project","menu:budget:cost"));
        if(!"budget".equals(metric))result.addAll(ProjectFundingDomainContributor.permissions("funding"));
        return Collections.unmodifiableSet(result);
    }
    public static boolean matches(String q){return q!=null && TERMS.stream().anyMatch(q::contains);}
    public static boolean hasExplicitMetric(String q){return q!=null && q.matches(".*(预算|实际|收支|全部利润|两类利润|两种利润).*" );}
    public static String metric(String q){boolean budget=q!=null && q.contains("预算"),actual=q!=null && (q.contains("实际") || q.contains("收支"));return budget&&!actual?"budget":actual&&!budget?"actual":"both";}
    @Override public BusinessDomainDescriptor descriptor(){return new BusinessDomainDescriptor(CODE,"项目利润",List.of("projectProfit.query","projectProfit.clarifyDate"),List.of(new DataScopeDescriptor("PROFIT_SOURCES","对应利润来源菜单及可见项目范围",List.of("MENU","PROJECT_MEMBER"))),"REALTIME",TERMS);}
    @Override public boolean availableTo(AgentUserContext user){return user!=null && (user.permissions().containsAll(permissions("budget")) || user.permissions().containsAll(permissions("actual")));}
}
