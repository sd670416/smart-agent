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

    @Test
    void requestsCompleteDefaultProjectOverviewColumns() {
        String instruction = new SystemInstructionCatalog().resolve("v1").orElseThrow();

        assertThat(instruction).contains(
                "项目名称、项目编号、所属组织、项目性质、联营单位、项目类型、建设单位、项目经理、项目状态、项目预算");
    }

    @Test
    void routesCompleteProjectDetailToSingleArchiveToolCall() {
        String instruction = new SystemInstructionCatalog().resolve("v1").orElseThrow();

        assertThat(instruction)
                .contains("project.getArchiveDetail", "项目详情", "完整档案", "不得重复调用")
                .contains("每个 AVAILABLE 分区", "Markdown 表格", "审批流程", "节点名称", "不得压缩成一行流程描述")
                .contains("看一下某个项目", "介绍一下某个项目", "单个项目")
                .contains("只询问状态、预算、进度等明确字段", "project.query")
                .contains("项目列表、统计、筛选、排序、分组和分页");
    }

    @Test
    void routesApprovalQueriesAndFollowUpsToReadOnlyApprovalTools() {
        String instruction = new SystemInstructionCatalog().resolve("v1").orElseThrow();

        assertThat(instruction)
                .contains("approval.query", "approval.getDetail", "待办", "已办", "我发起")
                .contains("本人范围", "流程去重", "第1页20条")
                .contains("下一页", "查看第 N 条", "沿用上一轮")
                .contains("Markdown 表格", "简体中文")
                .contains("禁止办理", "退回", "转办", "委托", "拿回", "终止");
    }
}
