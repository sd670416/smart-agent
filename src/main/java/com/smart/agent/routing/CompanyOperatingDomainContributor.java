package com.smart.agent.routing;
import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class CompanyOperatingDomainContributor implements BusinessDomainContributor {
    public static final String CODE="COMPANY_OPERATING";
    public static boolean matches(String question) {
        return question!=null && question.matches("(?s).*(?:自持公司经营|公司经营|按(?:自持)?公司|各(?:自持)?公司).*");
    }
    public static boolean isCompanyReference(String question) {
        return question!=null && !question.matches("(?s).*(?:改查|切换|转查).*")
                && question.matches("(?s).*(?:(?:这家|那家|该|此|这|那)公司|第[一二三四五六七八九十百零两0-9]+家(?:公司|的)).*");
    }
    public static boolean hasExplicitMetric(String question) {
        return question!=null && question.matches("(?s).*(?:项目数量|项目数|多少项目|多少个项目|合同|已收|收款|回款|收入|开票|发票|经营情况|经营概况|经营数据|全部指标|所有指标).*" );
    }
    public static String metric(String question) {
        if(question==null)return "all";
        if(question.matches("(?s).*(?:全部指标|所有指标|经营情况|经营概况|经营数据).*"))return "all";
        Set<String> found=new HashSet<>();
        if(question.matches("(?s).*(?:项目数量|项目数|多少项目|多少个项目).*"))found.add("projects");
        if(question.contains("合同"))found.add("contracts");
        if(question.matches("(?s).*(?:已收|收款|回款|收入).*"))found.add("receipts");
        if(question.matches("(?s).*(?:开票|发票).*"))found.add("invoice");
        return found.size()==1?found.iterator().next():"all";
    }
    public static List<String> permissions(String metric) {
        String m=metric==null?"all":metric;
        if(!Set.of("all","projects","contracts","receipts","invoice").contains(m))throw new IllegalArgumentException("Unsupported metric");
        var permissions=new ArrayList<>(List.of("menu:project"));
        if(Set.of("all","receipts").contains(m))permissions.addAll(List.of("menu:finance:payment-registration","menu:finance:other-income-registration"));
        if(Set.of("all","invoice").contains(m))permissions.add("menu:finance:invoice-application");return List.copyOf(permissions);
    }
    @Override public BusinessDomainDescriptor descriptor() {
        return new BusinessDomainDescriptor(CODE,"自持公司经营",List.of("companyOperating.query","companyOperating.clarifyDate"),
                List.of(new DataScopeDescriptor("SOURCE_MENU","项目菜单、来源菜单及可见项目",List.of("MENU","PROJECT_MEMBER"))),
                "REALTIME",List.of("自持公司经营","公司经营","按公司统计","各公司","按自持公司"));
    }
    @Override public boolean availableTo(AgentUserContext context) {return context!=null && context.permissions().contains("menu:project");}
}
