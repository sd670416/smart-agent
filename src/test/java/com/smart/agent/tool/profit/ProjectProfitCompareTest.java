package com.smart.agent.tool.profit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.tool.*;
import com.smart.agent.tool.project.*;
import com.smart.agent.routing.ProjectProfitDomainContributor;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectProfitCompareTest {
    private ProjectProfitCompareInput input(String json) throws Exception {
        return new ObjectMapper().readValue(json,ProjectProfitCompareInput.class);
    }
    @Test void acceptsOnlyCustomSameMetricSummaryGroups() throws Exception {
        assertThat(input("{\"comparisonType\":\"CUSTOM\",\"current\":{},\"baseline\":{}}")).isNotNull();
        for(String json:List.of("{\"comparisonType\":\"YOY\",\"current\":{},\"baseline\":{}}",
                "{\"comparisonType\":\"CUSTOM\",\"current\":{}}",
                "{\"comparisonType\":\"CUSTOM\",\"current\":{\"metric\":\"actual\"},\"baseline\":{}}",
                "{\"comparisonType\":\"CUSTOM\",\"current\":{\"mode\":\"detail\"},\"baseline\":{}}",
                "{\"comparisonType\":\"CUSTOM\",\"current\":{\"dateField\":\"projectCreateDate\"},\"baseline\":{}}"))
            assertThatThrownBy(()->input(json)).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
    @Test void missingIncomeMenuRefusesBeforeResolvingOrReadingGroups() throws Exception {
        var tool=new ProjectProfitCompareTool((context,input)->{throw new AssertionError("No reads permitted");},
                new ProjectReferenceResolver(new LocalProjectBusinessClient()));
        var permissions=new HashSet<>(ProjectProfitDomainContributor.permissions("both"));permissions.remove("menu:finance:other-income-registration");
        assertThatThrownBy(()->tool.execute(input("{\"comparisonType\":\"CUSTOM\",\"current\":{},\"baseline\":{}}"),
                new ToolContext("t","u","i",Set.of(),Set.of("p"),permissions)))
                .isInstanceOf(com.smart.agent.common.error.AgentException.class);
    }
}
