package com.smart.agent.tool.progress;

import com.smart.agent.clarification.ClarificationPrompt;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectProgressInteractionTest {
    private final ToolContext context=new ToolContext("t","u","i",Set.of(),Set.of("p1","p2"),Set.of("menu:project"));
    @Test void duplicateNamesAskBeforeReadingProgress() {
        var projects=mock(ProjectBusinessClient.class);
        when(projects.query(any(),any())).thenReturn(new ProjectQueryResult("detail",1,6,2L,false,List.of(),List.of(
            Map.of("projectId","p1","projectName","同名项目","projectCode","001"),
            Map.of("projectId","p2","projectName","同名项目","projectCode","002")),List.of()));
        AtomicInteger queries=new AtomicInteger();
        var tool=new ProjectProgressQueryTool((c,i)->{queries.incrementAndGet();return null;},new ProjectReferenceResolver(projects));
        var result=tool.execute(new ProjectProgressQueryInput(null,"同名项目","tasks",null,null,null,1,20),context);
        assertThat(result).isInstanceOf(ClarificationPrompt.class);
        var prompt=(ClarificationPrompt)result;
        assertThat(prompt.toString()).contains("PROJECT_REFERENCE","project:p1","project:p2","001","002");
        assertThat(queries.get()).isZero();
    }
    @Test void exactNameIsResolvedBeforeReadingProgress() {
        var projects=mock(ProjectBusinessClient.class);
        when(projects.query(any(),any())).thenReturn(new ProjectQueryResult("detail",1,6,1L,false,List.of(),List.of(
            Map.of("projectId","p2","projectName","乙项目","projectCode","002")),List.of()));
        var tool=new ProjectProgressQueryTool((c,i)->{
            assertThat(i.projectId()).isEqualTo("p2");assertThat(i.projectName()).isNull();
            return new ProjectQueryResult("detail",1,20,0L,false,List.of(),List.of(),List.of());
        },new ProjectReferenceResolver(projects));
        var result=(ProjectQueryResult)tool.execute(new ProjectProgressQueryInput(null,"乙项目","tasks",null,null,null,1,20),context);
        assertThat(result.appliedFilters().toString()).contains("projectId","p2");
    }
    @Test void dateClarificationDoesNotReadAmountsAndOffersOnlySupportedMeaning() {
        var prompt=new ProjectProgressClarifyDateTool().execute(new ProjectProgressClarifyDateTool.Input(),context);
        assertThat(prompt.toString()).contains("DATE_FIELD","date:projectCreateDate","date:allProjectDates");
        assertThat(prompt.toString()).doesNotContain("date:signingDate","date:createDate");
    }
}
