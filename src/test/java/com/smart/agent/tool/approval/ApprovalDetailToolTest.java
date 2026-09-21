package com.smart.agent.tool.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.smart.agent.common.error.AgentException;
import com.smart.agent.tool.ToolContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApprovalDetailToolTest {
    private final ApprovalBusinessClient client = mock(ApprovalBusinessClient.class);
    private final ApprovalDetailTool tool = new ApprovalDetailTool(client);
    private final ToolContext context = new ToolContext("tenant", "user", "identity", java.util.Set.of());

    @Test
    void resolvesDisplayedBusinessCodeOnlyWhenSingleVisibleTodoMatches() {
        when(client.query(eq(context), any())).thenReturn(new ApprovalQueryResult(1, 2, 1L,
                List.of(Map.of("processInstanceId", "4c1f0d9d-75e5-11f1-a1a0-cab1a76e91b0",
                        "fields", Map.of("processName", "项目报备00056A8V"))), Map.of(), List.of()));
        ApprovalDetailInput input = new ApprovalDetailInput("00056A8V", null, null, null, null, null);

        tool.execute(input, context);

        ArgumentCaptor<ApprovalDetailInput> resolved = ArgumentCaptor.forClass(ApprovalDetailInput.class);
        verify(client).detail(eq(context), resolved.capture());
        ArgumentCaptor<ApprovalQueryInput> query = ArgumentCaptor.forClass(ApprovalQueryInput.class);
        verify(client).query(eq(context), query.capture());
        assertThat(query.getValue().visibility()).isEqualTo("SELF");
        assertThat(resolved.getValue().processInstanceId())
                .isEqualTo("4c1f0d9d-75e5-11f1-a1a0-cab1a76e91b0");
    }

    @Test
    void refusesAmbiguousBusinessCodeWithoutCallingDetail() {
        when(client.query(eq(context), any())).thenReturn(new ApprovalQueryResult(1, 2, 2L,
                List.of(Map.of("processInstanceId", "id-1", "fields", Map.of("processName", "项目报备00056A8V")),
                        Map.of("processInstanceId", "id-2", "fields", Map.of("processName", "项目报备00056A8V"))),
                Map.of(), List.of()));

        assertThatThrownBy(() -> tool.execute(
                new ApprovalDetailInput("00056A8V", null, null, null, null, null), context))
                .isInstanceOf(AgentException.class)
                .satisfies(error -> assertThat(((AgentException) error).code())
                        .isEqualTo("AGENT_APPROVAL_DETAIL_AMBIGUOUS"));
        verify(client, never()).detail(eq(context), any());
    }

    @Test
    void exactProcessInstanceIdDoesNotQueryOtherTodos() {
        tool.execute(new ApprovalDetailInput("4c1f0d9d-75e5-11f1-a1a0-cab1a76e91b0",
                null, null, null, null, null), context);

        verify(client, never()).query(eq(context), any());
        verify(client).detail(eq(context), any());
    }
}
