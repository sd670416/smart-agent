package com.smart.agent.tool.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class ApprovalQueryToolTest {
    @Test
    void schemaSeparatesApprovalScopeFromVisibilityAndDocumentsAggregationRules() {
        ApprovalQueryTool tool = new ApprovalQueryTool(mock(ApprovalBusinessClient.class));

        assertThat(tool.argumentsSchemaJson())
                .contains("scope只允许TODO、PROCESSED、STARTED，不能填写SELF或ALL")
                .contains("visibility只允许SELF或ALL")
                .contains("统计总数时field必须省略")
                .contains("alias必须是英文字母开头的英文标识符");
    }
}
