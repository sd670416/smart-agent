package com.smart.agent.tool.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.agent.common.error.AgentException;
import org.junit.jupiter.api.Test;

class WebSearchPolicyTest {
    private final WebSearchPolicy policy = new WebSearchPolicy();

    @Test
    void allowsQueriesAboutPublicInternetInformation() {
        assertThatCode(() -> policy.validate("天津今日天气")).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("住建部最新公开政策")).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("人民币兑美元汇率")).doesNotThrowAnyException();
    }

    @Test
    void rejectsAuthenticationSecretsAndInternalAddresses() {
        assertSensitive("Authorization: Bearer abc.def.ghi");
        assertSensitive("查询 http://localhost:8080/internal 的内容");
        assertSensitive("访问 http://192.168.1.20/project");
    }

    @Test
    void rejectsDatabaseAndInternalBusinessContent() {
        assertSensitive("select * from bus_project where tenant_id = 1");
        assertSensitive("联网查询项目 1995777087260123138 的合同信息");
        assertSensitive("搜索附件地址 /attachments/5f20a1 的原文");
        assertSensitive("查询 tenantId 和 identityId 对应的权限");
    }

    private void assertSensitive(String query) {
        assertThatThrownBy(() -> policy.validate(query))
                .isInstanceOf(AgentException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((AgentException) error).code())
                        .isEqualTo("AGENT_WEB_SEARCH_SENSITIVE_INPUT"));
    }
}
