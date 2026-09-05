package com.smart.agent.tool.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.tool.ToolContext;
import org.springframework.web.reactive.function.client.WebClient;

public class SmartBootProjectBusinessClient implements ProjectBusinessClient {
    private final WebClient client; private final ObjectMapper mapper;
    public SmartBootProjectBusinessClient(WebClient client, ObjectMapper mapper){this.client=client;this.mapper=mapper;}
    @Override public ProjectOverviewResult getOverview(ToolContext context,String projectId){
        if(!context.canAccessProject(projectId)) throw new SecurityException("project access denied");
        try{return client.post().uri("/internal/ai/tools/project-overview").bodyValue(new Request(context.tenantId(),context.userId(),projectId)).retrieve().bodyToMono(String.class).map(s->{try{return mapper.readValue(s,ProjectOverviewResult.class);}catch(Exception e){throw new IllegalStateException(e);}}).block();}
        catch(RuntimeException e){throw e;}
    }
    private record Request(String tenantId,String userId,String projectId){}
}
