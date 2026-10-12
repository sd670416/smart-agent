package com.smart.agent.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentErrorMessageCatalogTest {

    @Test
    void permissionMessagesUseBusinessSubjectAndLeaveOtherErrorsUnchanged() {
        for (String subject : java.util.List.of("项目", "施工合同", "待办", "已办", "我发起的流程", "工程产值")) {
            assertThat(AgentErrorMessageCatalog.message("AGENT_TOOL_FORBIDDEN", false, subject))
                    .contains("当前账号还没有" + subject + "的查看权限", "管理员");
            assertThat(AgentErrorMessageCatalog.message("AGENT_TOOL_PROJECT_FORBIDDEN", false, subject))
                    .contains("所查询的" + subject + "信息", "查看范围").doesNotContain("开通");
        }
        assertThat(AgentErrorMessageCatalog.message("AGENT_TOOL_TIMEOUT", false, "施工合同"))
                .isEqualTo("业务数据查询超时，请稍后重试。");
        assertThat(AgentErrorMessageCatalog.message("AGENT_WEB_SEARCH_FORBIDDEN", false, "施工合同"))
                .contains("联网搜索").doesNotContain("施工合同");
    }

    @Test
    void returnsFriendlyMessagesForKnownAndUnknownErrors() {
        assertThat(AgentErrorMessageCatalog.message("AGENT_PROJECT_NOT_FOUND", false))
                .isEqualTo("未找到您有权访问的匹配项目，请检查项目名称或编号。");
        assertThat(AgentErrorMessageCatalog.message("AGENT_PROJECT_MENU_FORBIDDEN", false))
                .isEqualTo("当前账号还没有项目的查看权限，暂时无法为你查询。可以联系管理员确认并开通相关权限后再试。");
        assertThat(AgentErrorMessageCatalog.message("AGENT_TOOL_INVALID_INPUT", false))
                .isEqualTo("暂时无法识别本次查询条件，请换一种说法后重试。");
        assertThat(AgentErrorMessageCatalog.message("AGENT_QUERY_INVALID_REQUEST", false))
                .contains("项目统计查询", "明确统计方式");
        assertThat(AgentErrorMessageCatalog.message("AGENT_APPROVAL_QUERY_UNAVAILABLE", false))
                .contains("审批查询能力", "待办").doesNotContain("项目查询");
        assertThat(AgentErrorMessageCatalog.message("AGENT_APPROVAL_INVALID_REQUEST", false))
                .contains("审批查询条件", "待办").doesNotContain("项目统计查询");
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
