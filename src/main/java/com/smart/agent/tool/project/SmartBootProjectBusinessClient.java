package com.smart.agent.tool.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.tool.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import com.smart.agent.common.error.AgentException;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

public class SmartBootProjectBusinessClient implements ProjectBusinessClient {
    private static final Logger log = LoggerFactory.getLogger(SmartBootProjectBusinessClient.class);
    private final WebClient client;
    private final ObjectMapper mapper;
    private final String secret;

    public SmartBootProjectBusinessClient(WebClient client, ObjectMapper mapper,
                                          @Value("${AGENT_LOCAL_CONTEXT_SECRET}") String secret) {
        this.client = client;
        this.mapper = mapper;
        this.secret = secret;
    }

    @Override
    public ProjectOverviewResult getOverview(ToolContext context, String projectId) {
        if (!context.canAccessProject(projectId)) throw new SecurityException("project access denied");
        String path = "/internal/ai/tools/project-overview";
        try {
            return client.post().uri(path).headers(headers -> sign(headers, "POST", path))
                    .bodyValue(new Request(context.tenantId(), context.userId(), projectId)).retrieve().bodyToMono(String.class).map(s -> {
                try {
                    return mapper.readValue(s, ProjectOverviewResult.class);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).block();
        } catch (RuntimeException e) {
            throw e;
        }
    }

    @Override
    public ProjectContractsResult getContracts(ToolContext context, ProjectContractsInput input) {
        if (!context.canAccessProject(input.projectId())) throw new SecurityException("project access denied");
        String path = "/internal/ai/tools/project-contracts";
        return client.post().uri(path).headers(headers -> sign(headers, "POST", path)).bodyValue(input).retrieve()
                .bodyToMono(ProjectContractsResult.class).block();
    }

    @Override
    public AccessibleProjectsResult listAccessible(ToolContext context, AccessibleProjectsInput input) {
        log.info("调用项目列表工具: userId={}, trustedProjectCount={}, page={}, pageSize={}",
                context.userId(), context.projectIds().size(), input.page(), input.pageSize());
        String path = "/internal/ai/tools/projects";
        return client.post().uri(path).headers(headers -> sign(headers, "POST", path)).bodyValue(new ProjectsRequest(
                        context.projectIds(), input.keyword(), input.status(), input.page(), input.pageSize())).retrieve()
                .bodyToMono(AccessibleProjectsResult.class).block();
    }

    @Override
    public ProjectQueryResult query(ToolContext context, ProjectQueryInput input) {
        if (context.projectIds().isEmpty()) {
            return ProjectQueryResult.empty(input.page(), input.pageSize());
        }
        log.info("调用项目语义查询工具: userId={}, trustedProjectCount={}, page={}, pageSize={}",
                context.userId(), context.projectIds().size(), input.page(), input.pageSize());
        String path = "/internal/ai/tools/project-query";
        ProjectQueryRequest request = new ProjectQueryRequest(context.projectIds(), input.select(), input.filter(),
                input.groupBy(), input.aggregations(), input.orderBy(), input.page(), input.pageSize());
        try {
            return client.post().uri(path).headers(headers -> sign(headers, "POST", path)).bodyValue(request).retrieve()
                    .bodyToMono(ProjectQueryResult.class).block();
        } catch (WebClientResponseException exception) {
            String detail = exception.getResponseBodyAsString();
            if (detail == null || detail.isBlank()) detail = exception.getStatusText();
            String code = queryErrorCode(detail, exception.getStatusCode().value() == 400
                    ? "AGENT_QUERY_VALUE_INVALID" : "AGENT_TOOL_EXECUTION_FAILED");
            throw new AgentException(code,
                    exception.getStatusCode().is4xxClientError() ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY,
                    queryErrorMessage(detail));
        }
    }

    private String queryErrorCode(String response, String fallback) {
        try {
            String code = mapper.readTree(response).path("code").asText("");
            return switch (code) {
                case "PROJECT_QUERY_FIELD_UNKNOWN" -> "AGENT_QUERY_FIELD_UNKNOWN";
                case "PROJECT_QUERY_OPERATOR_INVALID" -> "AGENT_QUERY_OPERATOR_INVALID";
                case "PROJECT_QUERY_TOO_COMPLEX" -> "AGENT_QUERY_TOO_COMPLEX";
                case "PROJECT_QUERY_VALUE_INVALID" -> "AGENT_QUERY_VALUE_INVALID";
                default -> fallback;
            };
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String queryErrorMessage(String response) {
        try {
            String message = mapper.readTree(response).path("message").asText("");
            if (message != null && !message.isBlank()) {
                return message.length() > 1000 ? message.substring(0, 1000) : message;
            }
        } catch (Exception ignored) {
            // Keep the raw response when the downstream error is not JSON.
        }
        return response.length() > 1000 ? response.substring(0, 1000) : response;
    }

    private void sign(org.springframework.http.HttpHeaders headers, String method, String path) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        headers.set("X-Agent-Internal-Timestamp", timestamp);
        headers.set("X-Agent-Internal-Signature", sign(timestamp, method, path));
    }

    private String sign(String timestamp, String method, String path) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal((timestamp + "\n" + method + "\n" + path).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign internal request", e);
        }
    }

    private record ProjectsRequest(java.util.Set<String> projectIds, String keyword, String status,
                                   int page, int pageSize) {
    }

    private record Request(String tenantId, String userId, String projectId) {
    }

    private record ProjectQueryRequest(java.util.Set<String> projectIds, List<String> select,
                                       ProjectQueryFilter filter, List<String> groupBy,
                                       List<ProjectQueryAggregation> aggregations,
                                       List<ProjectQueryOrder> orderBy, int page, int pageSize) {
    }
}
