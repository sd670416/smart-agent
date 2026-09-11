package com.smart.agent.tool.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.ToolRisk;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CurrentTimeToolTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-11T06:30:00Z"), ZoneId.of("UTC"));
    private static final ToolContext CONTEXT = new ToolContext("tenant", "user", "identity", Set.of());

    @Test
    void returnsCurrentTimeInConfiguredTimezone() {
        CurrentTimeTool tool = new CurrentTimeTool(FIXED_CLOCK, ZoneId.of("Asia/Shanghai"));

        CurrentTimeResult result = tool.execute(new CurrentTimeInput(null), CONTEXT);

        assertThat(result.date()).isEqualTo("2026-09-11");
        assertThat(result.time()).isEqualTo("14:30:00");
        assertThat(result.dateTime()).isEqualTo("2026-09-11T14:30:00+08:00");
        assertThat(result.dayOfWeek()).isEqualTo("星期五");
        assertThat(result.timezone()).isEqualTo("Asia/Shanghai");
        assertThat(tool.key()).isEqualTo("system.current_time");
        assertThat(tool.risk()).isEqualTo(ToolRisk.L0);
        assertThat(tool.argumentsSchemaJson()).contains("timezone").doesNotContain("command", "path");
    }

    @Test
    void supportsExplicitValidTimezone() {
        CurrentTimeTool tool = new CurrentTimeTool(FIXED_CLOCK, ZoneId.of("Asia/Shanghai"));

        CurrentTimeResult result = tool.execute(new CurrentTimeInput("UTC"), CONTEXT);

        assertThat(result.time()).isEqualTo("06:30:00");
        assertThat(result.timezone()).isEqualTo("UTC");
    }

    @Test
    void rejectsInvalidTimezoneWithStableErrorCode() {
        CurrentTimeTool tool = new CurrentTimeTool(FIXED_CLOCK, ZoneId.of("Asia/Shanghai"));

        assertThatThrownBy(() -> tool.execute(new CurrentTimeInput("Invalid/Timezone"), CONTEXT))
                .isInstanceOf(com.smart.agent.common.error.AgentException.class)
                .satisfies(error -> assertThat(((com.smart.agent.common.error.AgentException) error).code())
                        .isEqualTo("AGENT_TIMEZONE_INVALID"));
    }
}
