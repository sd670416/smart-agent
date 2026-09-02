package com.smart.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smart.agent.common.error.AgentException;
import com.smart.agent.run.AgentRunService;
import com.smart.agent.security.AgentUserContext;
import com.smart.agent.tool.project.ProjectBusinessClient;
import com.smart.agent.tool.project.ProjectOverviewInput;
import com.smart.agent.tool.project.ProjectOverviewResult;
import com.smart.agent.tool.project.ProjectOverviewTool;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ToolExecutorTest {
    private ProjectBusinessClient projectBusinessClient;
    private AgentRunService agentRunService;
    private ToolExecutor executor;

    @BeforeEach
    void setUp() {
        projectBusinessClient = mock(ProjectBusinessClient.class);
        agentRunService = mock(AgentRunService.class);
        executor = new ToolExecutor(
                new ToolRegistry(Set.of(new ProjectOverviewTool(projectBusinessClient))),
                agentRunService,
                Duration.ofMillis(50));
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    void executesReadOnlyProjectToolWithTrustedToolContext() {
        when(projectBusinessClient.getOverview(any(), eq("project-1")))
                .thenReturn(new ProjectOverviewResult("project-1", "示例项目", "IN_PROGRESS", 0.42));

        Object result = executor.execute(
                "project.getOverview",
                new ProjectOverviewInput("project-1"),
                context(Set.of("project:read"), Set.of("project-1")));

        assertThat(result).isEqualTo(new ProjectOverviewResult("project-1", "示例项目", "IN_PROGRESS", 0.42));
        ArgumentCaptor<ToolContext> context = ArgumentCaptor.forClass(ToolContext.class);
        verify(projectBusinessClient).getOverview(context.capture(), eq("project-1"));
        assertThat(context.getValue().tenantId()).isEqualTo("tenant-1");
        assertThat(context.getValue().userId()).isEqualTo("user-1");
    }

    @Test
    void deniesMissingPermissionBeforeCallingBusinessClient() {
        assertThatThrownBy(() -> executor.execute(
                        "project.getOverview", new ProjectOverviewInput("project-1"), context(Set.of(), Set.of("project-1"))))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("permission");

        verifyNoInteractions(projectBusinessClient);
    }

    @Test
    void deniesProjectOutsideTrustedScopeBeforeCallingBusinessClient() {
        assertThatThrownBy(() -> executor.execute(
                        "project.getOverview",
                        new ProjectOverviewInput("project-2"),
                        context(Set.of("project:read"), Set.of("project-1"))))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("project scope");

        verifyNoInteractions(projectBusinessClient);
    }

    @Test
    void rejectsUnknownToolWithoutCallingBusinessClient() {
        assertThatThrownBy(() -> executor.execute(
                        "project.delete", Map.of(), context(Set.of("project:read"), Set.of("project-1"))))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("Unknown tool");

        verifyNoInteractions(projectBusinessClient);
    }

    @Test
    void rejectsUnknownInputFieldsWithoutCallingBusinessClient() {
        assertThatThrownBy(() -> executor.execute(
                        "project.getOverview",
                        Map.of("projectId", "project-1", "forgedScope", "project-2"),
                        context(Set.of("project:read"), Set.of("project-1"))))
                .isInstanceOf(AgentException.class)
                .hasMessage("Invalid tool input");

        verifyNoInteractions(projectBusinessClient);
    }

    @Test
    void stopsTimedOutToolWithoutLeakingClientFailureDetail() {
        when(projectBusinessClient.getOverview(any(), eq("project-1"))).thenAnswer(invocation -> {
            Thread.sleep(500);
            throw new IllegalStateException("secret business failure");
        });

        assertThatThrownBy(() -> executor.execute(
                        "project.getOverview",
                        new ProjectOverviewInput("project-1"),
                        context(Set.of("project:read"), Set.of("project-1"))))
                .isInstanceOf(AgentException.class)
                .hasMessage("Tool execution timed out")
                .satisfies(exception -> assertThat(exception.getMessage()).doesNotContain("secret business failure"));
    }

    @Test
    void rejectsOversizedSerializedResultWithoutReturningIt() {
        when(projectBusinessClient.getOverview(any(), eq("project-1")))
                .thenReturn(new ProjectOverviewResult("project-1", "x".repeat(70 * 1024), "IN_PROGRESS", 0.42));

        assertThatThrownBy(() -> executor.execute(
                        "project.getOverview",
                        new ProjectOverviewInput("project-1"),
                        context(Set.of("project:read"), Set.of("project-1"))))
                .isInstanceOf(AgentException.class)
                .hasMessage("Tool result exceeds size limit");
    }

    @Test
    void recordsOnlySafeStructuredExecutionSummary() {
        when(projectBusinessClient.getOverview(any(), eq("project-1")))
                .thenReturn(new ProjectOverviewResult("project-1", "项目名称不应出现在摘要中", "IN_PROGRESS", 0.42));

        executor.execute(
                "project.getOverview",
                new ProjectOverviewInput("project-1"),
                context(Set.of("project:read"), Set.of("project-1")));

        ArgumentCaptor<ToolExecutionSummary> summary = ArgumentCaptor.forClass(ToolExecutionSummary.class);
        verify(agentRunService).recordToolExecution(summary.capture());
        assertThat(summary.getValue().toolKey()).isEqualTo("project.getOverview");
        assertThat(summary.getValue().risk()).isEqualTo(ToolRisk.L1);
        assertThat(summary.getValue().outcome()).isEqualTo(ToolExecutionOutcome.SUCCEEDED);
        assertThat(summary.getValue().durationMillis()).isGreaterThanOrEqualTo(0);
        assertThat(summary.getValue().resultSizeBytes()).isPositive();
        assertThat(summary.getValue().toString()).doesNotContain("project-1", "项目名称不应出现在摘要中");
    }

    private static AgentUserContext context(Set<String> permissions, Set<String> projectIds) {
        return new AgentUserContext("tenant-1", "user-1", "identity-1", permissions, projectIds);
    }
}
