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

    @Test
    void returnsSpecificChineseMessagesForWebSearchErrors() {
        assertThat(AgentErrorMessageCatalog.message("AGENT_WEB_SEARCH_DISABLED", false))
                .contains("未启用联网搜索", "管理员");
        assertThat(AgentErrorMessageCatalog.message("AGENT_WEB_SEARCH_FORBIDDEN", false))
                .contains("暂无使用联网搜索的权限");
        assertThat(AgentErrorMessageCatalog.message("AGENT_WEB_SEARCH_SENSITIVE_INPUT", false))
                .contains("内部业务信息", "不能发送到公网");
        assertThat(AgentErrorMessageCatalog.message("AGENT_WEB_SEARCH_PROVIDER_UNSUPPORTED", false))
                .contains("当前模型", "不支持联网搜索");
        assertThat(AgentErrorMessageCatalog.message("AGENT_WEB_SEARCH_RATE_LIMITED", false))
                .contains("请求较多", "稍后重试");
        assertThat(AgentErrorMessageCatalog.message("AGENT_WEB_SEARCH_TIMEOUT", false))
                .contains("联网查询超时");
        assertThat(AgentErrorMessageCatalog.message("AGENT_WEB_SEARCH_FAILED", false))
                .contains("联网查询失败");
        assertThat(AgentErrorMessageCatalog.message("AGENT_WEB_SEARCH_NO_RESULTS", false))
                .contains("未找到可靠的公开来源", "关键词");
    }
}
