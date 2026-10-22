package com.smart.agent.tool.income;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OtherIncomeRegistrationQueryInputTest {
    @Test void itemsKeepParentIdFiltersAndRequestedPageThroughProjectResolution() throws Exception {
        var input = new ObjectMapper().readValue("{\"registrationId\":\" r1 \",\"mode\":\"items\",\"page\":2,\"pageSize\":1,\"dateField\":\"receivedDate\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}",
                OtherIncomeRegistrationQueryInput.class).withProjectId("p1");
        assertThat(input.registrationId()).isEqualTo("r1");
        assertThat(input.mode()).isEqualTo("items");
        assertThat(input.page()).isEqualTo(2);
        assertThat(input.projectId()).isEqualTo("p1");
        assertThat(input.dateField()).isEqualTo("receivedDate");
        assertThat(input.startDate()).isEqualTo("2026-01-01");
    }

    @Test void itemsWithoutRealParentIdCannotQueryEveryRegistration() {
        assertThatThrownBy(() -> new OtherIncomeRegistrationQueryInput(null, null, null, "items", null, null, null, 1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void omittedOptionsHaveBoundedDefaults() throws Exception {
        OtherIncomeRegistrationQueryInput input = new ObjectMapper().readValue("{}", OtherIncomeRegistrationQueryInput.class);
        assertThat(input.mode()).isEqualTo("detail");
        assertThat(input.page()).isEqualTo(1);
        assertThat(input.pageSize()).isEqualTo(20);
    }

    @Test void invalidDateFieldCannotReachTheBusinessApi() {
        assertThatThrownBy(() -> new OtherIncomeRegistrationQueryInput(null, null, null, "summary",
                "create_date OR 1=1", "2026-01-01", "2026-01-31", 1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void ambiguousDatesMustNotSilentlyDefaultToProjectDate() {
        assertThatThrownBy(() -> new OtherIncomeRegistrationQueryInput(null, null, null, "summary",
                null, "2026-01-01", "2026-01-31", 1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void excessivePagesAndReversedDatesAreRejected() {
        assertThatThrownBy(() -> new OtherIncomeRegistrationQueryInput(null, null, null, "detail",
                null, null, null, 0, 1000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OtherIncomeRegistrationQueryInput(null, null, null, "summary",
                "createDate", "2026-02-01", "2026-01-31", 1, 20)).isInstanceOf(IllegalArgumentException.class);
    }
}
