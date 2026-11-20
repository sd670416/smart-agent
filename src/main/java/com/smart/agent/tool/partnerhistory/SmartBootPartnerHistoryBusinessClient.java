package com.smart.agent.tool.partnerhistory;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

public class SmartBootPartnerHistoryBusinessClient implements PartnerHistoryBusinessClient {
    private static final String PATH = "/internal/ai/tools/partner-history-query";
    private final WebClient client;
    private final ObjectMapper mapper;
    private final String secret;
    public SmartBootPartnerHistoryBusinessClient(WebClient client, ObjectMapper mapper, String secret) {
        this.client = client; this.mapper = mapper; this.secret = secret;
    }
    @Override public ProjectQueryResult query(ToolContext context, PartnerHistoryQueryInput input) {
        if (input.projectId() != null && !context.canAccessProject(input.projectId()))
            throw new AgentException("AGENT_TOOL_FORBIDDEN", HttpStatus.FORBIDDEN, "当前账号暂不能查看该项目的合作历史");
        Map<String, Object> body = mapper.convertValue(input, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        body.remove("projectId"); body.remove("projectCode");
        body.put("projectCode", input.projectCode());
        body.put("projectIds", input.projectId() == null ? context.projectIds() : List.of(input.projectId()));
        try {
            String raw = client.post().uri(PATH).headers(h -> sign(h, context)).bodyValue(body).retrieve().bodyToMono(String.class).block();
            if (raw == null || raw.isBlank()) throw new AgentException("AGENT_TOOL_EXECUTION_FAILED", HttpStatus.BAD_GATEWAY, "合作历史查询响应为空，请稍后重试");
            return mapper.readerFor(ProjectQueryResult.class).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(raw);
        } catch (WebClientResponseException e) {
            HttpStatus status = e.getStatusCode().value() == 403 ? HttpStatus.FORBIDDEN : e.getStatusCode().is4xxClientError() ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
            String message = status == HttpStatus.FORBIDDEN ? "当前账号暂不能查看所选合作历史或相关业务明细"
                    : status == HttpStatus.BAD_REQUEST ? "请确认合作方、合同类型和签约日期条件" : "合作历史查询暂时未能完成，请稍后重试";
            if (status == HttpStatus.FORBIDDEN || status == HttpStatus.BAD_REQUEST) {
                try { String detail = mapper.readTree(e.getResponseBodyAsString()).path("message").asText("");
                    if (!detail.isBlank() && detail.length() <= 500) message = detail; } catch (java.io.IOException ignored) { }
            }
            throw new AgentException(status == HttpStatus.FORBIDDEN ? "AGENT_TOOL_FORBIDDEN" : status == HttpStatus.BAD_REQUEST ? "AGENT_QUERY_VALUE_INVALID" : "AGENT_TOOL_EXECUTION_FAILED", status, message);
        } catch (java.io.IOException e) {
            throw new AgentException("AGENT_TOOL_EXECUTION_FAILED", HttpStatus.BAD_GATEWAY, "合作历史返回数据暂时无法读取，请稍后重试");
        }
    }
    private void sign(HttpHeaders headers, ToolContext c) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String roles = String.join(",", new TreeSet<>(c.roleIds()));
        String permissions = String.join(",", new TreeSet<>(c.permissions()));
        String projects = String.join(",", new TreeSet<>(c.projectIds()));
        headers.set("X-Agent-Internal-Timestamp", timestamp); headers.set("X-Agent-Tenant-Id", c.tenantId());
        headers.set("X-Agent-User-Id", c.userId()); headers.set("X-Agent-Identity-Id", c.identityId());
        headers.set("X-Agent-Role-Ids", roles); headers.set("X-Agent-Permissions", permissions); headers.set("X-Agent-Project-Ids", projects);
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String value = timestamp + "\nPOST\n" + PATH + "\n" + c.tenantId() + "\n" + c.userId() + "\n" + c.identityId() + "\n" + roles + "\n" + permissions + "\n" + projects;
            headers.set("X-Agent-Internal-Signature", Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8))));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("Unable to sign partner history request", e); }
    }
}
