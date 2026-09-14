package com.smart.agent.tool.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smart.agent.tool.ToolContext;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectArchiveDetailToolTest {

    @Test
    void resolvesProjectCodeBeforeLoadingCompleteArchiveOnce() {
        ProjectBusinessClient client = mock(ProjectBusinessClient.class);
        ToolContext context = new ToolContext("tenant", "user", "identity", Set.of("project-1"));
        when(client.listAccessible(context, new AccessibleProjectsInput(null, null, 1, 20)))
                .thenReturn(new AccessibleProjectsResult(1, 20, 1, false, List.of(
                        new AccessibleProjectItem("project-1", "示例项目", "A001", "2", "已立项",
                                null, null, null))));
        ProjectArchiveDetailResult expected = new ProjectArchiveDetailResult(
                "project-1", "示例项目", List.of());
        when(client.getArchiveDetail(context, "project-1")).thenReturn(expected);

        ProjectArchiveDetailResult actual = new ProjectArchiveDetailTool(client)
                .execute(new ProjectArchiveDetailInput(null, "A001", null), context);

        assertThat(actual).isEqualTo(expected);
        assertThat(new ProjectArchiveDetailTool(client).description()).contains("完整项目档案");
        verify(client).getArchiveDetail(context, "project-1");
    }
}
