package com.smart.agent.tool.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.ToolRisk;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectQueryToolTest {
    @Test
    void normalizesChineseAggregationAliasAndMatchingOrderField() {
        ProjectQueryInput input = new ProjectQueryInput(
                List.of(), null, List.of("projectType"),
                List.of(new ProjectQueryAggregation("count", null, "项目数量")),
                List.of(new ProjectQueryOrder("项目数量", "DESC")), 1, 100);

        assertThat(input.aggregations().get(0).alias()).isEqualTo("projectCount");
        assertThat(input.orderBy().get(0).field()).isEqualTo("projectCount");
    }

    @Test
    void exposesOnlyBusinessQueryDslAndDelegatesWithTrustedContext() {
        ProjectBusinessClient client = mock(ProjectBusinessClient.class);
        ProjectQueryTool tool = new ProjectQueryTool(client);
        ProjectQueryInput input = new ProjectQueryInput(
                List.of("projectName", "projectStatus"),
                new ProjectQueryFilter("AND", List.of(
                        new ProjectQueryCondition("projectStatus", "eq", "已立项")), List.of()),
                List.of(), List.of(), List.of(), 1, 20);
        ToolContext context = new ToolContext("tenant", "user", "identity", Set.of("p1", "p2"));

        tool.execute(input, context);

        assertThat(tool.key()).isEqualTo("project.query");
        assertThat(tool.requiredPermission()).isEqualTo("menu:project");
        assertThat(tool.risk()).isEqualTo(ToolRisk.L1);
        assertThat(tool.argumentsSchemaJson())
                .contains("projectName", "filter", "conditions", "groupBy", "aggregations", "orderBy")
                .doesNotContain("tenantId", "userId", "projectIds", "sql", "column");
        verify(client).query(context, input);
    }

}
