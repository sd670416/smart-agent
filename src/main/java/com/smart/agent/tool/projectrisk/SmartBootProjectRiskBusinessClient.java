package com.smart.agent.tool.projectrisk;
import com.fasterxml.jackson.databind.*;
import com.smart.agent.common.error.AgentException;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.*;
public class SmartBootProjectRiskBusinessClient implements ProjectRiskBusinessClient {
    private static final String PATH="/internal/ai/tools/project-risk-query";
    private final WebClient client;private final ObjectMapper mapper;private final String secret;
    public SmartBootProjectRiskBusinessClient(WebClient client,ObjectMapper mapper,String secret){this.client=client;this.mapper=mapper;this.secret=secret;}
    @Override public ProjectQueryResult query(ToolContext c,ProjectRiskQueryInput input){
        if(input.projectId()!=null && !c.canAccessProject(input.projectId()))throw new AgentException("AGENT_TOOL_FORBIDDEN",HttpStatus.FORBIDDEN,"当前账号暂不能查看该项目的风险与工期");
        var body=mapper.valueToTree(input);((com.fasterxml.jackson.databind.node.ObjectNode)body).remove(List.of("projectCode","projectName"));
        try {
            String raw=client.post().uri(PATH).headers(h->sign(h,c)).bodyValue(body).retrieve().bodyToMono(String.class).block();
            if(raw==null)throw new IllegalStateException("Missing risk response");
            return mapper.readerFor(ProjectQueryResult.class).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(raw);
        } catch(WebClientResponseException e){
            HttpStatus status=e.getStatusCode().value()==403?HttpStatus.FORBIDDEN:e.getStatusCode().is4xxClientError()?HttpStatus.BAD_REQUEST:HttpStatus.BAD_GATEWAY;
            String message="项目风险与工期查询暂未完成，请稍后重试";
            if(status!=HttpStatus.BAD_GATEWAY)try{message=mapper.readTree(e.getResponseBodyAsString()).path("message").asText(message);}catch(java.io.IOException ignored){}
            throw new AgentException(status==HttpStatus.FORBIDDEN?"AGENT_TOOL_FORBIDDEN":status==HttpStatus.BAD_REQUEST?"AGENT_QUERY_VALUE_INVALID":"AGENT_TOOL_EXECUTION_FAILED",status,message);
        } catch(java.io.IOException e){throw new AgentException("AGENT_TOOL_EXECUTION_FAILED",HttpStatus.BAD_GATEWAY,"风险返回数据暂时无法读取");}
    }
    private void sign(HttpHeaders h,ToolContext c){
        String time=""+Instant.now().getEpochSecond(), roles=String.join(",",new TreeSet<>(c.roleIds())), permissions=String.join(",",new TreeSet<>(c.permissions())), projects=String.join(",",new TreeSet<>(c.projectIds()));
        h.set("X-Agent-Internal-Timestamp",time);h.set("X-Agent-Tenant-Id",c.tenantId());h.set("X-Agent-User-Id",c.userId());h.set("X-Agent-Identity-Id",c.identityId());h.set("X-Agent-Role-Ids",roles);h.set("X-Agent-Permissions",permissions);h.set("X-Agent-Project-Ids",projects);
        try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));String value=time+"\nPOST\n"+PATH+"\n"+c.tenantId()+"\n"+c.userId()+"\n"+c.identityId()+"\n"+roles+"\n"+permissions+"\n"+projects;h.set("X-Agent-Internal-Signature",Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8))));}catch(java.security.GeneralSecurityException e){throw new IllegalStateException("Cannot sign risk request",e);}
    }
}
