package com.smart.agent.tool.funding;

import com.smart.agent.routing.*;
import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectFundingQueryTest {
    @Test void fundingWinsOverIndividualSourcesAndProjectWords() {
        var resolver = new QueryIntentResolver(new BusinessDomainRegistry(List.of(new ProjectFundingDomainContributor(),
                new MaterialRefundDomainContributor(), new ProjectDomainContributor(), new PaymentRequestDomainContributor(),
                new ProjectReceiptsDomainContributor(), new ApprovalDomainContributor())));
        var user = new AgentUserContext("t","u","i", ProjectFundingDomainContributor.permissions("funding"),Set.of("p1"));
        for(String question : List.of("项目累计付款", "项目净付款", "项目垫资金额", "付款减去退货退款后垫资多少"))
            assertThat(resolver.resolve(question, Map.of(), "PROJECT", user).candidate().orElseThrow().domain().code()).isEqualTo("PROJECT_FUNDING");
        assertThat(resolver.resolve("查询项目垫资相关待办",Map.of(),"PROJECT_FUNDING",user).candidate().orElseThrow().domain().code()).isEqualTo("APPROVAL");
    }
    @Test void missingIncomePermissionCannotFallBackToProjectStatistics() {
        var resolver = new QueryIntentResolver(new BusinessDomainRegistry(List.of(new ProjectFundingDomainContributor(),new ProjectDomainContributor())));
        var user = new AgentUserContext("t","u","i",Set.of("menu:project","menu:finance:payment-request"),Set.of("p1"));
        assertThat(resolver.resolve("项目垫资多少",Map.of(),null,user).status()).isEqualTo(IntentResolution.Status.UNKNOWN);
    }
    @Test void dateRangeMustExplicitlyReferToProjectCreationAndModeCannotBecomeItems() {
        assertThatThrownBy(() -> new ProjectFundingQueryInput(null,null,null,"summary","funding","receivedDate","2026-01-01","2026-01-31",1,20,null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectFundingQueryInput(null,null,null,"items","funding",null,null,null,1,20,null))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void metricPermissionSetsAreCompleteAndDoNotRequireUnrelatedIncomeMenus() {
        assertThat(ProjectFundingDomainContributor.permissions("cumulative")).hasSize(3);
        assertThat(ProjectFundingDomainContributor.permissions("net")).hasSize(4);
        assertThat(ProjectFundingDomainContributor.permissions("funding")).hasSize(6);
        assertThatThrownBy(() -> ProjectFundingDomainContributor.permissions("profit")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void mentioningExcludedSourcesDoesNotDemandTheirMenus() {
        var resolver=new QueryIntentResolver(new BusinessDomainRegistry(List.of(new ProjectFundingDomainContributor(),
                new MaterialRefundDomainContributor(),new ProjectReceiptsDomainContributor(),new ProjectDomainContributor())));
        var paymentOnly=new AgentUserContext("t","u","i",ProjectFundingDomainContributor.permissions("cumulative"),Set.of("p1"));
        assertThat(resolver.resolve("查询累计付款，不扣材料退货退款",Map.of(),null,paymentOnly).candidate().orElseThrow().domain().code())
                .isEqualTo("PROJECT_FUNDING");
        var netOnly=new AgentUserContext("t","u","i",ProjectFundingDomainContributor.permissions("net"),Set.of("p1"));
        assertThat(resolver.resolve("净付款（尚未减已收项目款）多少",Map.of(),null,netOnly).candidate().orElseThrow().domain().code())
                .isEqualTo("PROJECT_FUNDING");
    }
}
