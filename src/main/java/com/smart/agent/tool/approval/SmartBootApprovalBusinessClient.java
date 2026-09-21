package com.smart.agent.tool.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.common.error.AgentException;
import com.smart.agent.tool.ToolContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.TreeSet;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

public class SmartBootApprovalBusinessClient implements ApprovalBusinessClient {
    private final WebClient client;
    private final ObjectMapper mapper;
    private final String secret;

    public SmartBootApprovalBusinessClient(WebClient client, ObjectMapper mapper,
                                           @Value("${AGENT_LOCAL_CONTEXT_SECRET}") String secret) {
        this.client = client;
        this.mapper = mapper;
        this.secret = secret;
    }

    @Override public ApprovalQueryResult query(ToolContext context, ApprovalQueryInput input) {
        return call(context, "/internal/ai/tools/approval-query", input, ApprovalQueryResult.class);
    }

    @Override public ApprovalDetailResult detail(ToolContext context, ApprovalDetailInput input) {
        return call(context, "/internal/ai/tools/approval-detail", input, ApprovalDetailResult.class);
    }

    private <T> T call(ToolContext context, String path, Object body, Class<T> type) {
        try {
            return client.post().uri(path).headers(headers -> sign(headers, context, path))
                    .bodyValue(body).retrieve().bodyToMono(type).block();
        } catch (WebClientResponseException exception) {
            String raw = exception.getResponseBodyAsString();
            String message = message(raw);
            HttpStatus status = exception.getStatusCode().value() == 403
                    ? HttpStatus.FORBIDDEN
                    : (exception.getStatusCode().is4xxClientError()
                    ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY);
            throw new AgentException(code(raw), status, message);
        }
    }

    private String code(String raw) {
        try {
            String code = mapper.readTree(raw).path("code").asText("");
            return switch (code) {
                case "APPROVAL_QUERY_FIELD_UNKNOWN" -> "AGENT_APPROVAL_FIELD_UNKNOWN";
                case "APPROVAL_QUERY_OPERATOR_INVALID" -> "AGENT_APPROVAL_OPERATOR_INVALID";
                case "APPROVAL_QUERY_VALUE_INVALID" -> "AGENT_APPROVAL_VALUE_INVALID";
                case "APPROVAL_QUERY_TOO_COMPLEX" -> "AGENT_APPROVAL_QUERY_TOO_COMPLEX";
                case "APPROVAL_QUERY_PERSON_AMBIGUOUS" -> "AGENT_APPROVAL_PERSON_AMBIGUOUS";
                case "APPROVAL_QUERY_PERSON_NOT_FOUND" -> "AGENT_APPROVAL_PERSON_NOT_FOUND";
                case "APPROVAL_QUERY_PERMISSION_DENIED" -> "AGENT_APPROVAL_PERMISSION_DENIED";
                case "APPROVAL_DETAIL_NOT_ACCESSIBLE" -> "AGENT_APPROVAL_DETAIL_NOT_ACCESSIBLE";
                default -> "AGENT_APPROVAL_QUERY_FAILED";
            };
        } catch (Exception ignored) { return "AGENT_APPROVAL_QUERY_FAILED"; }
    }

    private String message(String raw) {
        try {
            String value = mapper.readTree(raw).path("message").asText("");
            if (!value.isBlank()) return trim(value);
        } catch (Exception ignored) { }
        return trim(raw == null || raw.isBlank() ? "审批查询暂时无法完成，请稍后重试" : raw);
    }

    private String trim(String value) { return value.length() > 1000 ? value.substring(0, 1000) : value; }

    private void sign(org.springframework.http.HttpHeaders headers, ToolContext context, String path) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String roles = canonical(context.roleIds());
        String permissions = canonical(context.permissions());
        String projects = canonical(context.projectIds());
        headers.set("X-Agent-Internal-Timestamp", timestamp);
        headers.set("X-Agent-Tenant-Id", context.tenantId());
        headers.set("X-Agent-User-Id", context.userId());
        headers.set("X-Agent-Identity-Id", context.identityId());
        headers.set("X-Agent-Role-Ids", roles);
        headers.set("X-Agent-Permissions", permissions);
        headers.set("X-Agent-Project-Ids", projects);
        headers.set("X-Agent-Internal-Signature", signature(timestamp, path, context, roles, permissions, projects));
    }

    private String signature(String timestamp, String path, ToolContext c, String roles, String permissions, String projects) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String canonical = timestamp + "\nPOST\n" + path + "\n" + c.tenantId() + "\n" + c.userId()
                    + "\n" + c.identityId() + "\n" + roles + "\n" + permissions + "\n" + projects;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("Unable to sign internal request", exception); }
    }

    private String canonical(java.util.Set<String> values) { return String.join(",", new TreeSet<>(values)); }
}
