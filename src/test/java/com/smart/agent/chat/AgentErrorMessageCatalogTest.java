package com.smart.agent.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentErrorMessageCatalogTest {

    @Test
    void returnsFriendlyMessagesForKnownAndUnknownErrors() {
        assertThat(AgentErrorMessageCatalog.message("AGENT_PROJECT_NOT_FOUND", false))
                .isEqualTo("未找到您有权访问的匹配项目，请检查项目名称或编号。");
        assertThat(AgentErrorMessageCatalog.message("AGENT_TOOL_INVALID_INPUT", false))
                .isEqualTo("暂时无法识别本次查询条件，请换一种说法后重试。");
        assertThat(AgentErrorMessageCatalog.message("AGENT_QUERY_INVALID_REQUEST", false))
                .contains("项目统计查询", "明确统计方式");
        assertThat(AgentErrorMessageCatalog.message("UNEXPECTED_CODE", false))
                .isEqualTo("抱歉，本次请求暂时未能完成，请稍后重试。");
        assertThat(AgentErrorMessageCatalog.message("UNEXPECTED_CODE", false))
                .doesNotContain("Agent request failed");
        assertThat(AgentErrorMessageCatalog.queryClarification(
                "AGENT_QUERY_VALUE_INVALID", "不支持的项目状态：立项。可选项：已立项、立项审批中"))
                .contains("无法确定", "可选项：已立项、立项审批中", "请补充更明确的字段或条件");
    }
}
