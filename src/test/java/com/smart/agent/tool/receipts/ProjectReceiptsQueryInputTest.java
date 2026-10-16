package com.smart.agent.tool.receipts;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectReceiptsQueryInputTest {
    @Test void defaultsAndExactProjectSelectionArePreserved() {
        var input = new ProjectReceiptsQueryInput(null, " 示例 ", null, "receivedDate", "2026-01-01", "2026-01-31", null);
        assertThat(input.projectName()).isEqualTo("示例");
        assertThat(input.projectNameMatch()).isEqualTo("exact");
        var resolved = input.withProjectId("p1");
        assertThat(resolved.projectId()).isEqualTo("p1");
        assertThat(resolved.projectName()).isNull();
        assertThat(resolved.startDate()).isEqualTo("2026-01-01");
    }
    @Test void datesAndMatchingModesCannotBroadenTheQuerySilently() {
        assertThatThrownBy(() -> new ProjectReceiptsQueryInput(null, null, null, null, "2026-01-01", "2026-01-31", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectReceiptsQueryInput(null, null, null, "receivedDate", "2026-02-01", "2026-01-31", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectReceiptsQueryInput(null, null, null, "unsupported", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectReceiptsQueryInput("p1", "示例", null, null, null, null, "contains"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
