package com.smart.agent.routing;
import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CostContractRoutingTest {
    private final QueryIntentResolver resolver = new QueryIntentResolver(new BusinessDomainRegistry(List.of(
            new CostContractDomainContributor(),new ProjectDomainContributor(),new ApprovalDomainContributor(),new ConstructionContractDomainContributor())));
    private final AgentUserContext user = new AgentUserContext("t","u","i",Set.of("menu:cost-contract:labor"),Set.of("p"));
    @Test void categoryQueryUsesCostContractsRatherThanConstructionContracts() {
        assertEquals("COST_CONTRACT",resolver.resolve("查询劳务合同金额",Map.of(),"PROJECT",user).candidate().orElseThrow().domain().code());
        assertTrue(resolver.resolve("全部合同成本占比",Map.of(),"PROJECT",user).candidate().isEmpty());
        assertEquals("APPROVAL",resolver.resolve("劳务合同待办",Map.of(),"COST_CONTRACT",user).candidate().orElseThrow().domain().code());
    }
}
