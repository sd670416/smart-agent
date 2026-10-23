package com.smart.agent.tool.cost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.tool.*;
import com.smart.agent.tool.project.*;
import com.smart.agent.routing.CostContractDomainContributor;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CostContractCompareTest {
    private CostContractCompareTool tool(CostContractBusinessClient client) {
        return new CostContractCompareTool(client,new ProjectReferenceResolver(new LocalProjectBusinessClient()));
    }
    @Test void eachMissingSourcePermissionStopsBeforeProjectOrBusinessRead() throws Exception {
        var tool=tool((context,input)->{throw new AssertionError("Must not query");});
        var input=input("{\"comparisonType\":\"CUSTOM\",\"current\":{},\"baseline\":{}}");
        for(String missing:CostContractDomainContributor.permissions("all")) {
            var permissions=new HashSet<>(CostContractDomainContributor.permissions("all"));permissions.remove(missing);
            assertThatThrownBy(()->tool.execute(input,new ToolContext("t","u","i",Set.of(),Set.of("p"),permissions)))
                    .isInstanceOf(com.smart.agent.common.error.AgentException.class).hasMessageContaining("权限");
        }
    }
    private CostContractCompareInput input(String json) throws Exception {
        return new ObjectMapper().readValue(json,CostContractCompareInput.class);
    }
    @Test void requiresTwoAllSourceCurrentGroupsAndRejectsHistoricalComparison() throws Exception {
        assertThat(input("{\"comparisonType\":\"CUSTOM\",\"current\":{},\"baseline\":{}}")).isNotNull();
        for(String json:List.of("{\"comparisonType\":\"YOY\",\"current\":{},\"baseline\":{}}",
                "{\"comparisonType\":\"CUSTOM\",\"current\":{}}",
                "{\"comparisonType\":\"CUSTOM\",\"current\":{\"source\":\"material\"},\"baseline\":{}}",
                "{\"comparisonType\":\"CUSTOM\",\"current\":{\"dateField\":\"projectCreateDate\"},\"baseline\":{}}"))
            assertThatThrownBy(()->input(json)).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
    @Test void costDomainExposesComparisonAsSameBusinessModule() {
        assertThat(new CostContractDomainContributor().descriptor().toolKeys()).contains("costContract.compare");
    }
}
