package com.smart.agent.tool.operationcompare;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.common.error.AgentException;
import com.smart.agent.tool.ToolContext;
import com.smart.agent.tool.project.ProjectQueryResult;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

public class SmartBootProjectOperationCompareBusinessClient implements ProjectOperationCompareBusinessClient {
    private static final String PATH = "/internal/ai/tools/project-operation-compare";
    private final WebClient client;
    private final ObjectMapper mapper;
    private final String secret;
    public SmartBootProjectOperationCompareBusinessClient(WebClient client, ObjectMapper mapper, String secret) {
        this.client = client; this.mapper = mapper; this.secret = secret;
    }
    @Override public ProjectQueryResult compare(ToolContext context, ProjectOperationCompareInput input) {
        for (var item : input.projects()) if (item.projectId() == null || !context.canAccessProject(item.projectId()))
            throw new AgentException("AGENT_TOOL_PROJECT_FORBIDDEN", HttpStatus.FORBIDDEN, "所选比较项目不在当前可查看范围内");
        try {
            String raw = client.post().uri(PATH).headers(h -> sign(h, context)).bodyValue(input)
                    .retrieve().bodyToMono(String.class).block();
            if (raw == null || raw.isBlank()) throw new AgentException("AGENT_TOOL_EXECUTION_FAILED", HttpStatus.BAD_GATEWAY, "项目经营比较响应为空");
            return mapper.readerFor(ProjectQueryResult.class).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(raw);
        } catch (WebClientResponseException failure) {
            HttpStatus status = failure.getStatusCode().value() == 403 ? HttpStatus.FORBIDDEN
                    : failure.getStatusCode().is4xxClientError() ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
            String message = status == HttpStatus.FORBIDDEN ? "当前账号暂不能查看所选项目的经营比较"
                    : "项目经营比较暂时无法完成，请稍后重试";
            if (status == HttpStatus.BAD_REQUEST) {
                message = "请确认比较项目、指标和日期条件";
                try { var error = mapper.readTree(failure.getResponseBodyAsString());
                    if (error.path("code").asText().equals("PROJECT_OPERATION_COMPARE_INVALID") && !error.path("message").asText().isBlank())
                        message = error.path("message").asText();
                } catch (java.io.IOException ignored) { }
            }
            throw new AgentException(status == HttpStatus.FORBIDDEN ? "AGENT_TOOL_FORBIDDEN" : status == HttpStatus.BAD_REQUEST
                    ? "AGENT_QUERY_VALUE_INVALID" : "AGENT_TOOL_EXECUTION_FAILED", status, message);
        } catch (java.io.IOException failure) {
            throw new AgentException("AGENT_TOOL_EXECUTION_FAILED", HttpStatus.BAD_GATEWAY, "项目经营比较响应格式无效");
        }
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
        } catch (java.security.GeneralSecurityException failure) { throw new IllegalStateException("Unable to sign internal request", failure); }
    }
}
