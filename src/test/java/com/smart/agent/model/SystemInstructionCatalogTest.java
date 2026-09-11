package com.smart.agent.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SystemInstructionCatalogTest {
    @Test
    void routesOnlyPublicInformationToWebSearch() {
        String instruction = new SystemInstructionCatalog().resolve("v1").orElseThrow();

        assertThat(instruction)
                .contains("web.search", "天气", "新闻", "公开政策", "公开来源")
                .contains("内部项目", "合同", "附件", "知识库原文", "不得发送到公网")
                .contains("只发送本轮经过清洗的公开搜索词")
                .contains("始终使用简体中文");
    }
}
