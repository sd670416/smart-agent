package com.smart.agent.tool.funding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.tool.*;
import com.smart.agent.tool.project.*;
import com.smart.agent.routing.ProjectFundingDomainContributor;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectFundingCompareTest {
    private ProjectFundingCompareInput input(String json) throws Exception {
        return new ObjectMapper().readValue(json,ProjectFundingCompareInput.class);
    }
    @Test void acceptsOnlyCustomSameMetricSummaryGroups() throws Exception {
        assertThat(input("{\"comparisonType\":\"CUSTOM\",\"current\":{},\"baseline\":{}}")).isNotNull();
        for(String json:List.of("{\"comparisonType\":\"YOY\",\"current\":{},\"baseline\":{}}",
                "{\"comparisonType\":\"CUSTOM\",\"current\":{}}",
                "{\"comparisonType\":\"CUSTOM\",\"current\":{\"metric\":\"net\"},\"baseline\":{}}",
                "{\"comparisonType\":\"CUSTOM\",\"current\":{\"mode\":\"detail\"},\"baseline\":{}}",
                "{\"comparisonType\":\"CUSTOM\",\"current\":{\"dateField\":\"projectCreateDate\"},\"baseline\":{}}"))
            assertThatThrownBy(()->input(json)).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
    @Test void missingIncomeMenuRefusesBeforeResolvingOrReadingGroups() throws Exception {
        var tool=new ProjectFundingCompareTool((context,input)->{throw new AssertionError("No reads permitted");},
                new ProjectReferenceResolver(new LocalProjectBusinessClient()));
        var permissions=new HashSet<>(ProjectFundingDomainContributor.permissions("funding"));permissions.remove("menu:finance:other-income-registration");
        assertThatThrownBy(()->tool.execute(input("{\"comparisonType\":\"CUSTOM\",\"current\":{},\"baseline\":{}}"),
                new ToolContext("t","u","i",Set.of(),Set.of("p"),permissions)))
                .isInstanceOf(com.smart.agent.common.error.AgentException.class);
    }
}
