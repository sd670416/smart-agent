package com.smart.agent.tool.web;

import com.smart.agent.common.error.AgentException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

public final class WebSearchPolicy {
    private static final Pattern JWT = Pattern.compile("(?i)\\beyJ[a-z0-9_-]{8,}\\.[a-z0-9_-]{8,}\\.[a-z0-9_-]{8,}\\b");
    private static final Pattern LONG_ID = Pattern.compile("(?<!\\d)\\d{16,}(?!\\d)");
    private static final Pattern SQL = Pattern.compile(
            "(?is)\\b(select\\s+.+\\s+from|insert\\s+into|update\\s+.+\\s+set|delete\\s+from|drop\\s+table|alter\\s+table)\\b");
    private static final Pattern PRIVATE_ADDRESS = Pattern.compile(
            "(?i)(localhost|127\\.0\\.0\\.1|10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|192\\.168\\.\\d{1,3}\\.\\d{1,3}|172\\.(1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3})");
    private static final List<String> SENSITIVE_MARKERS = List.of(
            "authorization", "bearer ", "api-key", "api_key", "access_token", "cookie:",
            "/attachments/", "attachmentid", "tenantid", "tenant_id", "identityid", "identity_id",
            "roleid", "role_id", "userid", "user_id", "isdeleted", "is_deleted", "数据库结构", "权限信息");
    private static final List<String> INTERNAL_BUSINESS_MARKERS = List.of(
            "内部项目", "项目合同", "项目人员", "项目供应商", "合同信息", "附件原文", "知识库原文");

    public void validate(String query) {
        if (query == null) return;
        String normalized = query.toLowerCase(Locale.ROOT);
        boolean sensitiveMarker = SENSITIVE_MARKERS.stream().anyMatch(normalized::contains);
        boolean internalBusiness = INTERNAL_BUSINESS_MARKERS.stream().anyMatch(normalized::contains);
        if (sensitiveMarker || internalBusiness || JWT.matcher(query).find() || LONG_ID.matcher(query).find()
                || SQL.matcher(query).find() || PRIVATE_ADDRESS.matcher(query).find()) {
            throw new AgentException("AGENT_WEB_SEARCH_SENSITIVE_INPUT", HttpStatus.BAD_REQUEST,
                    "Sensitive or internal information cannot be sent to web search");
        }
    }
}
