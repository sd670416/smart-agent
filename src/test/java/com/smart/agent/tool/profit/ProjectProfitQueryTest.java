package com.smart.agent.tool.profit;

import com.smart.agent.routing.*;
import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectProfitQueryTest {
    @Test void profitIsNotRoutedToItsBudgetOrFundingSources(){
        var routing=new QueryIntentResolver(new BusinessDomainRegistry(List.of(new ProjectProfitDomainContributor(),new BudgetCostDomainContributor(),new ProjectFundingDomainContributor(),new ProjectDomainContributor(),new ApprovalDomainContributor())));
        var user=new AgentUserContext("t","u","i",ProjectProfitDomainContributor.permissions("both"),Set.of("p"));
        for(String q:List.of("预算利润多少","项目实际利润率","预算成本和净付款相关的项目利润"))
            assertThat(routing.resolve(q,Map.of(),null,user).candidate().orElseThrow().domain().code()).isEqualTo("PROJECT_PROFIT");
        assertThat(routing.resolve("项目利润相关待办",Map.of(),null,user).candidate().orElseThrow().domain().code()).isEqualTo("APPROVAL");
        assertThat(routing.resolve("看第一个项目",Map.of(),"PROJECT_PROFIT",user).candidate().orElseThrow().domain().code()).isEqualTo("PROJECT_PROFIT");
        assertThat(routing.resolve("看第一个项目的档案",Map.of(),"PROJECT_PROFIT",user).candidate().orElseThrow().domain().code()).isEqualTo("PROJECT");
        var limited=new AgentUserContext("t","u","i",Set.of("menu:project"),Set.of("p"));
        assertThat(routing.resolve("项目利润",Map.of(),null,limited).status()).isEqualTo(IntentResolution.Status.UNKNOWN);
    }
    @Test void permissionsFollowSelectedProfitSources(){
        assertThat(ProjectProfitDomainContributor.permissions("budget")).containsExactlyInAnyOrder("menu:project","menu:budget:cost");
        assertThat(ProjectProfitDomainContributor.permissions("actual")).hasSize(6).doesNotContain("menu:budget:cost");
        assertThat(ProjectProfitDomainContributor.permissions("both")).hasSize(8);
        assertThatThrownBy(()->new ProjectProfitQueryInput(null,null,null,"compare","both",null,null,null,1,20,null)).isInstanceOf(IllegalArgumentException.class);
    }
}
