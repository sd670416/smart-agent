package com.smart.agent.tool.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.tool.ToolContext;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmartBootProjectBusinessClient implements ProjectBusinessClient {
    private static final Logger log = LoggerFactory.getLogger(SmartBootProjectBusinessClient.class);
    private final WebClient client; private final ObjectMapper mapper;
    public SmartBootProjectBusinessClient(WebClient client, ObjectMapper mapper){this.client=client;this.mapper=mapper;}
    @Override public ProjectOverviewResult getOverview(ToolContext context,String projectId){
        if(!context.canAccessProject(projectId)) throw new SecurityException("project access denied");
        try{return client.post().uri("/internal/ai/tools/project-overview").bodyValue(new Request(context.tenantId(),context.userId(),projectId)).retrieve().bodyToMono(String.class).map(s->{try{return mapper.readValue(s,ProjectOverviewResult.class);}catch(Exception e){throw new IllegalStateException(e);}}).block();}
        catch(RuntimeException e){throw e;}
    }
    @Override public ProjectContractsResult getContracts(ToolContext context, ProjectContractsInput input) {
        if (!context.canAccessProject(input.projectId())) throw new SecurityException("project access denied");
        return client.post().uri("/internal/ai/tools/project-contracts").bodyValue(input).retrieve()
                .bodyToMono(ProjectContractsResult.class).block();
    }
    @Override public AccessibleProjectsResult listAccessible(ToolContext context, AccessibleProjectsInput input) {
        log.info("调用项目列表工具: userId={}, trustedProjectCount={}, page={}, pageSize={}",
                context.userId(), context.projectIds().size(), input.page(), input.pageSize());
        return client.post().uri("/internal/ai/tools/projects").bodyValue(new ProjectsRequest(
                context.projectIds(), input.keyword(), input.status(), input.page(), input.pageSize())).retrieve()
                .bodyToMono(AccessibleProjectsResult.class).block();
    }
    private record ProjectsRequest(java.util.Set<String> projectIds, String keyword, String status,
                                   int page, int pageSize) {}
    private record Request(String tenantId,String userId,String projectId){}
}
