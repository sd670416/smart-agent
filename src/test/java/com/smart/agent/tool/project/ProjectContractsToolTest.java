package com.smart.agent.tool.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.agent.tool.ToolContext;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectContractsToolTest {

    @Test
    void resolvesProjectCodeToTrustedProjectId() {
        ProjectBusinessClient client = mock(ProjectBusinessClient.class);
        ToolContext context = new ToolContext("tenant", "user", "identity", Set.of("real-id"));
        AccessibleProjectItem item = new AccessibleProjectItem(
                "real-id", "联营项目", "20260709001", "2", "已立项", null, null, null);
        when(client.listAccessible(context, new AccessibleProjectsInput(null, null, 1, 20)))
                .thenReturn(new AccessibleProjectsResult(1, 20, 1, false, List.of(item)));
        ProjectContractsResult expected = new ProjectContractsResult(
                "real-id", "联营项目", List.of(
                        new ProjectArchiveDetailResult.Section("contract", "合同信息", "AVAILABLE",
                                java.util.Map.of("施工合同名称", "施工合同001"), List.of(), null),
                        new ProjectArchiveDetailResult.Section("subcontract", "分包合同信息", "EMPTY",
                                java.util.Map.of(), List.of(), null)));
        when(client.getContracts(context, new ProjectContractsInput("real-id", null, 1, 20)))
                .thenReturn(expected);

        ProjectContractsResult result = new ProjectContractsTool(client)
                .execute(new ProjectContractsInput("20260709001", null, 1, 20), context);

        assertThat(result).isEqualTo(expected);
        verify(client).getContracts(context, new ProjectContractsInput("real-id", null, 1, 20));
    }
}
