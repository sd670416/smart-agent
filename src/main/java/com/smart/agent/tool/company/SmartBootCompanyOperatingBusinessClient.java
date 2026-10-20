package com.smart.agent.tool.company;
import com.fasterxml.jackson.databind.*;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;
import com.smart.agent.common.error.AgentException;
import org.springframework.web.reactive.function.client.*;
import org.springframework.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class SmartBootCompanyOperatingBusinessClient implements CompanyOperatingBusinessClient {
    private static final String PATH="/internal/ai/tools/company-operating-query";
    private final WebClient client;
    private final ObjectMapper mapper;
    private final String secret;
    public SmartBootCompanyOperatingBusinessClient(WebClient client,ObjectMapper mapper,String secret){this.client=client;this.mapper=mapper;this.secret=secret;}
    @Override public ProjectQueryResult query(ToolContext context,CompanyOperatingQueryInput input) {
        if(input.projectId()!=null&&!context.canAccessProject(input.projectId()))throw new SecurityException("Project is outside trusted scope");
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("projectIds",input.projectId()==null?context.projectIds():List.of(input.projectId()));
        body.put("companyId",input.companyId());body.put("companyName",input.companyName());body.put("metric",input.metric());body.put("mode",input.mode());
        body.put("dateField",input.dateField());body.put("startDate",input.startDate());body.put("endDate",input.endDate());body.put("page",input.page());body.put("pageSize",input.pageSize());
        org.slf4j.LoggerFactory.getLogger(getClass()).info("调用公司经营查询: userId={}, trustedProjectCount={}, metric={}, mode={}, page={}",context.userId(),context.projectIds().size(),input.metric(),input.mode(),input.page());
        try {
            String raw=client.post().uri(PATH).headers(h->sign(h,context)).bodyValue(body).retrieve().bodyToMono(String.class).block();
            if(raw==null||raw.isBlank())throw new AgentException("AGENT_TOOL_EXECUTION_FAILED",HttpStatus.BAD_GATEWAY,"公司经营查询响应为空");
            return mapper.readerFor(ProjectQueryResult.class).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(raw);
        } catch(WebClientResponseException e) {
            if(e.getStatusCode().value()==422)throw new AgentException("AGENT_QUERY_VALUE_INVALID",HttpStatus.UNPROCESSABLE_ENTITY,"部分已审批业务缺少金额，暂时无法准确计算公司经营合计，请先核实原始单据");
            HttpStatus status=e.getStatusCode().value()==403?HttpStatus.FORBIDDEN:e.getStatusCode().is4xxClientError()?HttpStatus.BAD_REQUEST:HttpStatus.BAD_GATEWAY;
            throw new AgentException(status==HttpStatus.FORBIDDEN?"AGENT_TOOL_FORBIDDEN":status==HttpStatus.BAD_REQUEST?"AGENT_QUERY_VALUE_INVALID":"AGENT_TOOL_EXECUTION_FAILED",status,
                    status==HttpStatus.FORBIDDEN?"当前账号尚未开通所查询公司经营指标的查看权限":status==HttpStatus.BAD_REQUEST?"请确认公司、项目、指标和日期筛选条件":"公司经营查询暂时失败，请稍后重试");
        } catch(java.io.IOException e) {throw new AgentException("AGENT_TOOL_EXECUTION_FAILED",HttpStatus.BAD_GATEWAY,"公司经营查询响应格式无效");}
    }
    private void sign(HttpHeaders headers,ToolContext context) {
        String timestamp=String.valueOf(Instant.now().getEpochSecond());
        String roles=String.join(",",new TreeSet<>(context.roleIds())),permissions=String.join(",",new TreeSet<>(context.permissions())),projects=String.join(",",new TreeSet<>(context.projectIds()));
        headers.set("X-Agent-Internal-Timestamp",timestamp);headers.set("X-Agent-Tenant-Id",context.tenantId());headers.set("X-Agent-User-Id",context.userId());headers.set("X-Agent-Identity-Id",context.identityId());
        headers.set("X-Agent-Role-Ids",roles);headers.set("X-Agent-Permissions",permissions);headers.set("X-Agent-Project-Ids",projects);
        try {
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            String canonical=timestamp+"\nPOST\n"+PATH+"\n"+context.tenantId()+"\n"+context.userId()+"\n"+context.identityId()+"\n"+roles+"\n"+permissions+"\n"+projects;
            headers.set("X-Agent-Internal-Signature",Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
        } catch(java.security.GeneralSecurityException e) {throw new IllegalStateException("Unable to sign internal request",e);}
    }
}
