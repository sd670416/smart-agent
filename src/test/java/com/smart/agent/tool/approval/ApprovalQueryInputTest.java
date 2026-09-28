package com.smart.agent.tool.approval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApprovalQueryInputTest {
    @Test
    void normalizesModelEnumValuesBeforeCallingSmartBoot() {
        ApprovalQueryInput input = new ApprovalQueryInput(
                " todo ", "self", null, List.of(), null, List.of(), List.of(), List.of(),
                1, 20, "process", null);

        assertThat(input.scope()).isEqualTo("TODO");
        assertThat(input.visibility()).isEqualTo("SELF");
        assertThat(input.recordMode()).isEqualTo("PROCESS");
    }

    @Test
    void normalizesChineseApprovalAliases() {
        assertValues("待办", "本人", "流程", "TODO", "SELF", "PROCESS");
        assertValues("已办", "我的", "办理记录", "PROCESSED", "SELF", "OPERATION");
        assertValues("我发起", "全部", "操作记录", "STARTED", "ALL", "OPERATION");
        assertValues("已办", "所有人", "流程去重", "PROCESSED", "ALL", "PROCESS");
    }

    @Test
    void normalizesModelDeduplicatedModeToProcess() {
        assertValues("todo", "self", "deduplicated", "TODO", "SELF", "PROCESS");
    }

    @Test
    void movesVisibilityValueOutOfScope() {
        ApprovalQueryInput input = input("SELF", null, List.of());

        assertThat(input.scope()).isEqualTo("TODO");
        assertThat(input.visibility()).isEqualTo("SELF");
    }

    @Test
    void preservesExplicitVisibilityWhenScopeContainsVisibilityValue() {
        ApprovalQueryInput input = input("本人", "ALL", List.of());

        assertThat(input.scope()).isEqualTo("TODO");
        assertThat(input.visibility()).isEqualTo("ALL");
    }

    @Test
    void normalizesCountAggregationGeneratedByModel() {
        Map<String, Object> aggregation = new LinkedHashMap<>();
        aggregation.put("function", " COUNT ");
        aggregation.put("field", "流程实例ID");
        aggregation.put("alias", "审批数量");

        ApprovalQueryInput input = input("TODO", "SELF", List.of(aggregation));

        assertThat(input.aggregations()).containsExactly(Map.of(
                "function", "count",
                "alias", "metric1"));
    }

    @Test
    void keepsSupportedAggregationFieldAndAsciiAlias() {
        Map<String, Object> aggregation = new LinkedHashMap<>();
        aggregation.put("function", "sum");
        aggregation.put("field", "项目预算");
        aggregation.put("alias", "totalBudget");

        ApprovalQueryInput input = input("PROCESSED", "SELF", List.of(aggregation));

        assertThat(input.aggregations()).containsExactly(Map.of(
                "function", "sum",
                "field", "项目预算",
                "alias", "totalBudget"));
    }

    private ApprovalQueryInput input(String scope, String visibility,
                                     List<Map<String, Object>> aggregations) {
        return new ApprovalQueryInput(scope, visibility, null, List.of(), null,
                List.of(), aggregations, List.of(), 1, 20, "PROCESS", "项目报备");
    }

    private void assertValues(String scope, String visibility, String mode,
                              String expectedScope, String expectedVisibility, String expectedMode) {
        ApprovalQueryInput input = new ApprovalQueryInput(scope, visibility, null,
                List.of(), null, List.of(), List.of(), List.of(), 1, 20, mode, null);
        assertThat(input.scope()).isEqualTo(expectedScope);
        assertThat(input.visibility()).isEqualTo(expectedVisibility);
        assertThat(input.recordMode()).isEqualTo(expectedMode);
    }
}
