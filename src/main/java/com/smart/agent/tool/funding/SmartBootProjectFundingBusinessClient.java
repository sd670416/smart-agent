package com.smart.agent.tool.funding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.smart.agent.common.error.AgentException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class SmartBootProjectFundingBusinessClient implements ProjectFundingBusinessClient {
    private static final String PATH = "/internal/ai/tools/project-funding-query";
    private final WebClient client;
    private final ObjectMapper mapper;
    private final String secret;

    public SmartBootProjectFundingBusinessClient(WebClient client, ObjectMapper mapper, String secret) {
        this.client = client;
        this.mapper = mapper;
        this.secret = secret;
    }

    @Override public ProjectQueryResult query(ToolContext context, ProjectFundingQueryInput input) {
        return post(PATH,context,body(context,input));
    }
    @Override public ProjectQueryResult compare(ToolContext context,ProjectFundingCompareInput input) {
        Map<String,Object> request=new LinkedHashMap<>();request.put("comparisonType",input.comparisonType());
        request.put("current",body(context,input.current()));request.put("baseline",body(context,input.baseline()));
        return post("/internal/ai/tools/project-funding-compare",context,request);
    }
    private Map<String,Object> body(ToolContext context,ProjectFundingQueryInput input) {
        if (input.projectId() != null && !context.canAccessProject(input.projectId()))
            throw new SecurityException("Project is outside trusted scope");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectIds", input.projectId() == null ? context.projectIds() : List.of(input.projectId()));
        body.put("projectName", input.projectName());
        body.put("metric", input.metric());
        body.put("mode", input.mode());
        body.put("dateField", input.dateField());
        body.put("startDate", input.startDate());
        body.put("endDate", input.endDate());
        body.put("page", input.page());
        body.put("pageSize", input.pageSize());
        return body;
    }
    private ProjectQueryResult post(String path,ToolContext context,Map<String,Object> body) {
        org.slf4j.LoggerFactory.getLogger(getClass()).info(
                "调用总付款与项目垫资查询工具: userId={}, trustedProjectCount={}, mode={}, page={}, pageSize={}",
                context.userId(), context.projectIds().size(), body.get("mode"), body.get("page"), body.get("pageSize"));
        try {
            String raw = client.post().uri(path).headers(headers -> sign(headers, context,path))
                    .bodyValue(body).retrieve().bodyToMono(String.class).block();
            if (raw == null || raw.isBlank()) throw new AgentException("AGENT_TOOL_EXECUTION_FAILED",
                    HttpStatus.BAD_GATEWAY, "总付款与项目垫资查询响应为空");
            return mapper.readerFor(ProjectQueryResult.class).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readValue(raw);
        } catch (WebClientResponseException exception) {
            if(exception.getStatusCode().value()==422)
                throw new AgentException("AGENT_QUERY_VALUE_INVALID",HttpStatus.UNPROCESSABLE_ENTITY,
                        "部分有效收支记录缺少金额，暂时无法准确计算付款或垫资合计及比较，请先核实金额");
            HttpStatus status = exception.getStatusCode().value() == 403 ? HttpStatus.FORBIDDEN
                    : exception.getStatusCode().is4xxClientError() ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
            throw new AgentException(status == HttpStatus.FORBIDDEN ? "AGENT_TOOL_FORBIDDEN"
                    : status == HttpStatus.BAD_REQUEST ? "AGENT_QUERY_VALUE_INVALID" : "AGENT_TOOL_EXECUTION_FAILED",
                    status, status == HttpStatus.BAD_REQUEST ? "总付款与项目垫资查询参数无效，请确认筛选字段和日期范围" : "总付款与项目垫资查询暂时失败");
        } catch (java.io.IOException exception) {
            throw new AgentException("AGENT_TOOL_EXECUTION_FAILED", HttpStatus.BAD_GATEWAY, "总付款与项目垫资查询响应格式无效");
        }
    }

    private void sign(HttpHeaders headers, ToolContext context,String path) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String roles = String.join(",", new TreeSet<>(context.roleIds()));
        String permissions = String.join(",", new TreeSet<>(context.permissions()));
        String projects = String.join(",", new TreeSet<>(context.projectIds()));
        headers.set("X-Agent-Internal-Timestamp", timestamp);
        headers.set("X-Agent-Tenant-Id", context.tenantId());
        headers.set("X-Agent-User-Id", context.userId());
        headers.set("X-Agent-Identity-Id", context.identityId());
        headers.set("X-Agent-Role-Ids", roles);
        headers.set("X-Agent-Permissions", permissions);
        headers.set("X-Agent-Project-Ids", projects);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String canonical = timestamp + "\nPOST\n" + path + "\n" + context.tenantId() + "\n" + context.userId()
                    + "\n" + context.identityId() + "\n" + roles + "\n" + permissions + "\n" + projects;
            headers.set("X-Agent-Internal-Signature", Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign internal request", exception);
        }
    }
}
