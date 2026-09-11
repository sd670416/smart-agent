package com.smart.agent.tool.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.agent.common.error.AgentException;
import com.smart.agent.tool.ToolContext;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectOverviewToolTest {

    @Test
    void resolvesProjectCodePassedAsProjectIdWithinTrustedScope() {
        ProjectBusinessClient client = mock(ProjectBusinessClient.class);
        ToolContext context = new ToolContext("tenant", "user", "identity", Set.of("real-id"));
        AccessibleProjectItem item = new AccessibleProjectItem(
                "real-id", "测试项目", "XG0000001", "1", "已立项", null, null, null);
        when(client.listAccessible(context, new AccessibleProjectsInput(null, null, 1, 20)))
                .thenReturn(new AccessibleProjectsResult(1, 20, 1, false, List.of(item)));
        ProjectOverviewResult overview = new ProjectOverviewResult("real-id", "测试项目", "1", 0.5);
        when(client.getOverview(context, "real-id")).thenReturn(overview);

        ProjectOverviewResult result = new ProjectOverviewTool(client)
                .execute(new ProjectOverviewInput("XG0000001"), context);

        assertThat(result).isEqualTo(overview);
        verify(client).getOverview(context, "real-id");
    }

    @Test
    void reportsProjectNotFoundWithDedicatedErrorCode() {
        ProjectBusinessClient client = mock(ProjectBusinessClient.class);
        ToolContext context = new ToolContext("tenant", "user", "identity", Set.of("real-id"));
        when(client.listAccessible(context, new AccessibleProjectsInput(null, null, 1, 20)))
                .thenReturn(new AccessibleProjectsResult(1, 20, 0, false, List.of()));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        new ProjectOverviewTool(client).execute(new ProjectOverviewInput("不存在的项目"), context))
                .isInstanceOf(AgentException.class)
                .satisfies(error -> assertThat(((AgentException) error).code()).isEqualTo("AGENT_PROJECT_NOT_FOUND"));
    }
}
