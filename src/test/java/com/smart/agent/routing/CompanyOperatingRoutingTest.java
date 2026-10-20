package com.smart.agent.routing;
import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CompanyOperatingRoutingTest {
    private final QueryIntentResolver resolver=new QueryIntentResolver(new BusinessDomainRegistry(List.of(
            new CompanyOperatingDomainContributor(),new ProjectDomainContributor(),new ApprovalDomainContributor(),new ConstructionContractDomainContributor(),new ProjectReceiptsDomainContributor(),new InvoiceApplicationDomainContributor())));
    private final AgentUserContext user=new AgentUserContext("t","u","i",Set.copyOf(CompanyOperatingDomainContributor.permissions("all")),Set.of("p"));
    @Test void explicitCompanyGroupingDoesNotFallIntoProjectOrIncomeDomain() {
        for(String question:List.of("查看各自持公司经营情况","按自持公司统计项目数量","各公司已收项目款","各自持公司的施工合同金额","按公司汇总开票申请金额"))
            assertEquals("COMPANY_OPERATING",resolver.resolve(question,Map.of(),"PROJECT",user).candidate().orElseThrow().domain().code(),question);
    }
    @Test void workflowSwitchAndOrdinaryProjectQueriesKeepOwnDomains() {
        assertEquals("APPROVAL",resolver.resolve("自持公司项目待办",Map.of(),"COMPANY_OPERATING",user).candidate().orElseThrow().domain().code());
        assertEquals("PROJECT",resolver.resolve("查询项目列表",Map.of(),"COMPANY_OPERATING",user).candidate().orElseThrow().domain().code());
    }
    @Test void companyOrdinalWithMetricDoesNotSwitchToGlobalContractOrInvoiceQuery() {
        for(String question:List.of("第一家公司的施工合同金额呢","第一家公司的开票申请金额呢","这家公司的已收项目款呢"))
            assertEquals("COMPANY_OPERATING",resolver.resolve(question,Map.of(),"COMPANY_OPERATING",user).candidate().orElseThrow().domain().code(),question);
        assertEquals("CONSTRUCTION_CONTRACT",resolver.resolve("改查施工合同列表",Map.of(),"COMPANY_OPERATING",user).candidate().orElseThrow().domain().code());
    }
    @Test void companyFollowupStaysActiveAndSingleMetricDoesNotRequireAllMenus() {
        assertEquals("COMPANY_OPERATING",resolver.resolve("查看第一家详情",Map.of(),"COMPANY_OPERATING",user).candidate().orElseThrow().domain().code());
        var projectsOnly=new AgentUserContext("t","u","i",Set.of("menu:project"),Set.of("p"));
        assertEquals("COMPANY_OPERATING",resolver.resolve("按公司统计项目数量",Map.of(),"PROJECT",projectsOnly).candidate().orElseThrow().domain().code());
        assertEquals("COMPANY_OPERATING",resolver.resolve("按公司继续看下一页",Map.of(),"COMPANY_OPERATING",projectsOnly).candidate().orElseThrow().domain().code());
        assertEquals("COMPANY_OPERATING",resolver.resolve("公司经营继续看下一页",Map.of(),"COMPANY_OPERATING",projectsOnly).candidate().orElseThrow().domain().code());
        assertTrue(resolver.resolve("各公司已收项目款",Map.of(),"PROJECT",projectsOnly).candidate().isEmpty());
    }
}
