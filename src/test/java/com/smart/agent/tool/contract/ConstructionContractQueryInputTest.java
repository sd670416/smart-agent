package com.smart.agent.tool.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConstructionContractQueryInputTest {
    @Test void recordsRequireParentIdAndPreserveTypeWhenResolvingProject() throws Exception {
        var mapper = new ObjectMapper();
        assertThatCode(() -> mapper.readValue("{\"mode\":\"records\",\"contractId\":\"c1\",\"recordType\":\"supplement\"}", ConstructionContractQueryInput.class)).doesNotThrowAnyException();
        var input = mapper.readValue("{\"mode\":\"records\",\"contractId\":\"c1\",\"recordType\":\"supplement\"}", ConstructionContractQueryInput.class);
        assertThat(mapper.valueToTree(input.withProjectId("p1")).path("recordType").asText()).isEqualTo("supplement");
        assertThatThrownBy(() -> mapper.readValue("{\"mode\":\"records\"}", ConstructionContractQueryInput.class)).hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"recordType\":\"supplement\"}", ConstructionContractQueryInput.class)).hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"mode\":\"records\",\"contractId\":\"c1\",\"recordType\":\"other\"}", ConstructionContractQueryInput.class)).hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConstructionContractCompareInput("CUSTOM", null, input, input)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void exactContractReferenceSurvivesProjectResolutionAndCannotBeCompared() throws Exception {
        var mapper = new ObjectMapper();
        assertThatCode(() -> mapper.readValue("{\"contractId\":\"c1\"}", ConstructionContractQueryInput.class))
                .doesNotThrowAnyException();
        var input = mapper.readValue("{\"contractId\":\"c1\"}", ConstructionContractQueryInput.class);
        assertThat(mapper.valueToTree(input.withProjectId("p1")).path("contractId").asText()).isEqualTo("c1");
        assertThatThrownBy(() -> new ConstructionContractCompareInput("CUSTOM", null, input, input))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void projectTypeGroupingSurvivesCanonicalProjectResolution() throws Exception {
        var input = new ObjectMapper().readValue("{\"mode\":\"summary\",\"groupBy\":\"projectType\",\"projectTypeName\":\"土建\"}", ConstructionContractQueryInput.class);
        var resolved = new ObjectMapper().valueToTree(input.withProjectId("p1"));
        assertThat(resolved.path("groupBy").asText()).isEqualTo("projectType");
        assertThat(resolved.path("projectTypeName").asText()).isEqualTo("土建");
    }

    @Test void unsupportedGroupingAndGroupedDetailAreRejected() {
        var mapper = new ObjectMapper();
        assertThatThrownBy(() -> mapper.readValue("{\"mode\":\"summary\",\"groupBy\":\"company\"}", ConstructionContractQueryInput.class)).hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"mode\":\"detail\",\"groupBy\":\"projectType\"}", ConstructionContractQueryInput.class)).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
    @Test void omittedOptionsHaveBoundedDefaults() throws Exception {
        ConstructionContractQueryInput input = new ObjectMapper().readValue("{}", ConstructionContractQueryInput.class);
        assertThat(input.mode()).isEqualTo("detail");
        assertThat(input.page()).isEqualTo(1);
        assertThat(input.pageSize()).isEqualTo(20);
    }

    @Test void invalidDateFieldCannotReachTheBusinessApi() {
        assertThatThrownBy(() -> new ConstructionContractQueryInput(null, null, null, "summary",
                "create_date OR 1=1", "2026-01-01", "2026-01-31", 1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void ambiguousDatesMustNotSilentlyDefaultToProjectDate() {
        assertThatThrownBy(() -> new ConstructionContractQueryInput(null, null, null, "summary",
                null, "2026-01-01", "2026-01-31", 1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void excessivePagesAndReversedDatesAreRejected() {
        assertThatThrownBy(() -> new ConstructionContractQueryInput(null, null, null, "detail",
                null, null, null, 0, 1000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConstructionContractQueryInput(null, null, null, "summary",
                "signingDate", "2026-02-01", "2026-01-31", 1, 20)).isInstanceOf(IllegalArgumentException.class);
    }
}
