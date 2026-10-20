package com.smart.agent.routing;
import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CostContractDomainContributor implements BusinessDomainContributor {
    public static final String CODE="COST_CONTRACT";
    private static final Map<String,String> TERMS=Map.of("材料合同","material","劳务合同","labor",
            "机械合同","machine","专业分包合同","major","其他支出合同","other","其他付款合同","other");
    public static boolean matches(String question) {
        return question!=null && (question.contains("合同成本") || question.contains("合同费用占比")
                || TERMS.keySet().stream().anyMatch(question::contains));
    }
    public static String source(String question) {
        if(question!=null && question.contains("占比"))return "all";
        Set<String> sources=new HashSet<>();
        TERMS.forEach((term,source)->{if(question!=null && question.contains(term))sources.add(source);});
        return sources.size()==1 ? sources.iterator().next() : "all";
    }
    public static List<String> permissions(String source) {
        List<String> sources=source==null || "all".equals(source)
                ? List.of("material","labor","machine","major","other") : List.of(source);
        if(!List.of("material","labor","machine","major","other").containsAll(sources))throw new IllegalArgumentException("Unsupported source");
        return sources.stream().map(s->"menu:cost-contract:"+s).toList();
    }
    @Override public BusinessDomainDescriptor descriptor() {
        return new BusinessDomainDescriptor(CODE,"成本合同",List.of("costContract.query","costContract.clarifyDate"),
                List.of(new DataScopeDescriptor("SOURCE_MENU","各类合同菜单及可见项目",List.of("MENU","PROJECT_MEMBER"))),
                "REALTIME",List.of("合同成本","合同费用占比","材料合同","劳务合同","机械合同","专业分包合同","其他支出合同"));
    }
    @Override public boolean availableTo(AgentUserContext context) {
        return context!=null && permissions("all").stream().anyMatch(context.permissions()::contains);
    }
}
