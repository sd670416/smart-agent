package com.smart.agent.routing;

import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectProgressRoutingTest {
    private final QueryIntentResolver resolver=new QueryIntentResolver(new BusinessDomainRegistry(List.of(
        new ProjectProgressDomainContributor(),new ProjectDomainContributor(),new ApprovalDomainContributor())));
    private final AgentUserContext user=new AgentUserContext("t","u","i",Set.of("menu:project"),Set.of("p1","p2"));
    @Test void ordinalStaysInProgressButExplicitRequestsSwitchDomains() {
        for(String question:List.of("第二个项目的任务明细","看看第2个项目","这个项目的任务明细","查看该项目的任务"))
            assertThat(resolver.resolve(question,Map.of(),"PROJECT_PROGRESS",user).candidate().orElseThrow().domain().code()).isEqualTo("PROJECT_PROGRESS");
        for(String question:List.of("查询施工进度待办","改查待办","查询已办","查询我发起的流程"))
            assertThat(resolver.resolve(question,Map.of(),"PROJECT_PROGRESS",user).candidate().orElseThrow().domain().code()).isEqualTo("APPROVAL");
        assertThat(resolver.resolve("查看第二个项目的档案",Map.of(),"PROJECT_PROGRESS",user).candidate().orElseThrow().domain().code()).isEqualTo("PROJECT");
    }
}
