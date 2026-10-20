package com.smart.agent.tool.budget;

import com.smart.agent.routing.*;
import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BudgetCostQueryTest {
    @Test void budgetUsesOwnDomainAndWorkflowStillUsesApproval() {
        var resolver=new QueryIntentResolver(new BusinessDomainRegistry(List.of(new BudgetCostDomainContributor(),new ProjectDomainContributor(),new ApprovalDomainContributor())));
        var user=new AgentUserContext("t","u","i",Set.of("menu:budget:cost"),Set.of("p1"));
        for(String q:List.of("项目预算成本多少","目标成本明细","项目预算金额"))
            assertThat(resolver.resolve(q,Map.of(),"PROJECT",user).candidate().orElseThrow().domain().code()).isEqualTo("BUDGET_COST");
        assertThat(resolver.resolve("预算成本相关待办",Map.of(),"BUDGET_COST",user).candidate().orElseThrow().domain().code()).isEqualTo("APPROVAL");
        var limited=new AgentUserContext("t","u","i",Set.of("menu:project"),Set.of("p1"));
        assertThat(resolver.resolve("项目预算成本",Map.of(),null,limited).status()).isEqualTo(IntentResolution.Status.UNKNOWN);
    }
    @Test void itemModeNeedsOneProjectAndDatesNeedExplicitDimension() {
        assertThatThrownBy(()->new BudgetCostQueryInput(null,null,null,"items",null,null,null,1,20,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new BudgetCostQueryInput(null,null,null,"summary",null,"2026-01-01","2026-01-31",1,20,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new BudgetCostQueryInput("p",null,null,"summary","createDate",null,null,1,20,null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new BudgetCostQueryInput("p",null,null,"items",null,null,null,1,20,null).mode()).isEqualTo("items");
    }
}
