package com.smart.agent.tool.profit.ranking;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.common.error.AgentException;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.TreeSet;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

public class SmartBootProjectProfitRankingBusinessClient implements ProjectProfitRankingBusinessClient {
    private static final String PATH = "/internal/ai/tools/project-profit-ranking";
    private final WebClient client;
    private final ObjectMapper mapper;
    private final String secret;
    public SmartBootProjectProfitRankingBusinessClient(WebClient client, ObjectMapper mapper, String secret) {
        this.client = client; this.mapper = mapper; this.secret = secret;
    }
    @Override public ProjectQueryResult query(ToolContext context, ProjectProfitRankingInput input) {
        if (input.projectId() != null && !context.canAccessProject(input.projectId()))
            throw new AgentException("AGENT_TOOL_PROJECT_FORBIDDEN", HttpStatus.FORBIDDEN, "该项目不在当前可查询范围内");
        try {
            String raw = client.post().uri(PATH).headers(h -> sign(h, context)).bodyValue(input)
                    .retrieve().bodyToMono(String.class).block();
            if (raw == null || raw.isBlank()) throw new AgentException("AGENT_TOOL_EXECUTION_FAILED", HttpStatus.BAD_GATEWAY, "利润排名响应为空，请稍后重试");
            return mapper.readerFor(ProjectQueryResult.class).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(raw);
        } catch (WebClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 403) throw new AgentException("AGENT_TOOL_FORBIDDEN", HttpStatus.FORBIDDEN, "当前账号暂不能查看所选利润排名，请确认对应数据的查看权限");
            if (status == 400 || status == 422) throw new AgentException("AGENT_QUERY_VALUE_INVALID", HttpStatus.BAD_REQUEST,
                    businessMessage(exception.getResponseBodyAsString()));
            throw new AgentException("AGENT_TOOL_EXECUTION_FAILED", HttpStatus.BAD_GATEWAY, "利润排名暂时查询失败，请稍后重试");
        } catch (java.io.IOException exception) {
            throw new AgentException("AGENT_TOOL_EXECUTION_FAILED", HttpStatus.BAD_GATEWAY, "利润排名返回数据暂时无法读取，请稍后重试");
        }
    }
    private String businessMessage(String raw) {
        try {
            var body = mapper.readTree(raw);
            if ("AGENT_QUERY_VALUE_INVALID".equals(body.path("code").asText())) {
                String message = body.path("message").asText("");
                if (!message.isBlank() && message.length() <= 500) return message;
            }
        } catch (Exception ignored) { }
        return "请确认排名指标、查询范围与日期条件，目前支持当前累计利润排名";
    }
    private void sign(HttpHeaders headers, ToolContext context) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String roles = String.join(",", new TreeSet<>(context.roleIds()));
        String permissions = String.join(",", new TreeSet<>(context.permissions()));
        String projects = String.join(",", new TreeSet<>(context.projectIds()));
        headers.set("X-Agent-Internal-Timestamp", timestamp); headers.set("X-Agent-Tenant-Id", context.tenantId());
        headers.set("X-Agent-User-Id", context.userId()); headers.set("X-Agent-Identity-Id", context.identityId());
        headers.set("X-Agent-Role-Ids", roles); headers.set("X-Agent-Permissions", permissions); headers.set("X-Agent-Project-Ids", projects);
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String canonical = timestamp + "\nPOST\n" + PATH + "\n" + context.tenantId() + "\n" + context.userId()
                    + "\n" + context.identityId() + "\n" + roles + "\n" + permissions + "\n" + projects;
            headers.set("X-Agent-Internal-Signature", Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
        } catch (java.security.GeneralSecurityException exception) { throw new IllegalStateException("Unable to sign profit ranking request", exception); }
    }
}
