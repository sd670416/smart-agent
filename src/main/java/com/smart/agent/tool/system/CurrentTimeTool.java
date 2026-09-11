package com.smart.agent.tool.system;

import com.smart.agent.common.error.AgentException;
import com.smart.agent.tool.AgentTool;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.ToolRisk;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class CurrentTimeTool implements AgentTool<CurrentTimeInput, CurrentTimeResult> {
    private static final String SCHEMA = """
            {"type":"object","properties":{
              "timezone":{"type":"string","description":"IANA时区名称，可省略，例如Asia/Shanghai"}
            },"additionalProperties":false}
            """;
    private static final Map<DayOfWeek, String> WEEKDAYS = Map.of(
            DayOfWeek.MONDAY, "星期一", DayOfWeek.TUESDAY, "星期二", DayOfWeek.WEDNESDAY, "星期三",
            DayOfWeek.THURSDAY, "星期四", DayOfWeek.FRIDAY, "星期五", DayOfWeek.SATURDAY, "星期六",
            DayOfWeek.SUNDAY, "星期日");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final Clock clock;
    private final ZoneId defaultZone;

    public CurrentTimeTool(Clock clock, ZoneId defaultZone) {
        this.clock = clock;
        this.defaultZone = defaultZone;
    }

    @Override public String key() { return "system.current_time"; }
    @Override public Class<CurrentTimeInput> inputType() { return CurrentTimeInput.class; }
    @Override public String requiredPermission() { return ""; }
    @Override public ToolRisk risk() { return ToolRisk.L0; }
    @Override public String description() {
        return "获取指定时区的当前日期、时间、星期和时区，用于解析今天、本月、今年等相对时间";
    }
    @Override public String argumentsSchemaJson() { return SCHEMA; }

    @Override
    public CurrentTimeResult execute(CurrentTimeInput input, ToolContext context) {
        ZoneId zone = defaultZone;
        try {
            if (input != null && input.timezone() != null) zone = ZoneId.of(input.timezone());
        } catch (DateTimeException exception) {
            throw new AgentException("AGENT_TIMEZONE_INVALID", HttpStatus.BAD_REQUEST, "Invalid timezone");
        }
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zone);
        return new CurrentTimeResult(now.format(DATE), now.format(TIME), now.format(DATE_TIME),
                WEEKDAYS.get(now.getDayOfWeek()), zone.getId());
    }
}
