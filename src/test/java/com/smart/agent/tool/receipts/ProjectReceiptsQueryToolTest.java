package com.smart.agent.tool.receipts;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectReceiptsQueryToolTest {
    @Test void bothMenusAreRequiredEvenWhenCallingToolsDirectly() {
        var query = new ProjectReceiptsQueryTool((context, input) -> { throw new AssertionError("must not query"); },
                new ProjectReferenceResolver(new LocalProjectBusinessClient()));
        for (var permissions : java.util.List.of(Set.of("menu:finance:payment-registration"),
                Set.of("menu:finance:other-income-registration"))) {
            var context = new ToolContext("t", "u", "i", Set.of(), Set.of("p1"), permissions);
            assertThatThrownBy(() -> query.execute(new ProjectReceiptsQueryInput(null, null, null, null, null, null, null), context))
                    .isInstanceOf(com.smart.agent.common.error.AgentException.class)
                    .extracting("code").isEqualTo("AGENT_TOOL_FORBIDDEN");
            assertThatThrownBy(() -> new ProjectReceiptsClarifyDateTool().execute(new ProjectReceiptsClarifyDateTool.Input(), context))
                    .isInstanceOf(com.smart.agent.common.error.AgentException.class)
                    .extracting("code").isEqualTo("AGENT_TOOL_FORBIDDEN");
        }
    }
}
