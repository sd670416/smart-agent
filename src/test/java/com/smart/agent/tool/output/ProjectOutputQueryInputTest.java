package com.smart.agent.tool.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectOutputQueryInputTest {
    @Test void omittedOptionsHaveBoundedDefaults() throws Exception {
        ProjectOutputQueryInput input = new ObjectMapper().readValue("{}", ProjectOutputQueryInput.class);
        assertThat(input.mode()).isEqualTo("detail");
        assertThat(input.page()).isEqualTo(1);
        assertThat(input.pageSize()).isEqualTo(20);
    }

    @Test void invalidDateFieldCannotReachTheBusinessApi() {
        assertThatThrownBy(() -> new ProjectOutputQueryInput(null, null, null, "summary",
                "create_date OR 1=1", "2026-01-01", "2026-01-31", 1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void ambiguousDatesMustNotSilentlyDefaultToProjectDate() {
        assertThatThrownBy(() -> new ProjectOutputQueryInput(null, null, null, "summary",
                null, "2026-01-01", "2026-01-31", 1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void excessivePagesAndReversedDatesAreRejected() {
        assertThatThrownBy(() -> new ProjectOutputQueryInput(null, null, null, "detail",
                null, null, null, 0, 1000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectOutputQueryInput(null, null, null, "summary",
                "createDate", "2026-02-01", "2026-01-31", 1, 20)).isInstanceOf(IllegalArgumentException.class);
    }
}

