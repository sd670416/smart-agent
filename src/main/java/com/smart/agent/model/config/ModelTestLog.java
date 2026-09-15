package com.smart.agent.model.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_model_test_log")
public class ModelTestLog {
    @Id @Column(length = 36) private String id;
    @Column(name = "model_id", nullable = false, length = 36) private String modelId;
    @Column(name = "tenant_id", length = 36) private String tenantId;
    @Column(name = "user_id", nullable = false, length = 36) private String userId;
    @Column(name = "test_item", nullable = false, length = 32) private String testItem;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "http_status") private Integer httpStatus;
    @Column(name = "duration_millis") private Long durationMillis;
    @Column(name = "response_model_name", length = 128) private String responseModelName;
    @Column(name = "safe_response_summary", length = 2000) private String safeResponseSummary;
    @Column(name = "error_type", length = 128) private String errorType;
    @Column(name = "safe_error_detail", length = 2000) private String safeErrorDetail;
    @Column(name = "create_time", nullable = false) private Instant createdAt;

    protected ModelTestLog() {
    }

    static ModelTestLog from(String modelId, String tenantId, String userId,
            ModelTestResult.Item result, String responseModelName) {
        ModelTestLog log = new ModelTestLog();
        log.id = UUID.randomUUID().toString();
        log.modelId = modelId;
        log.tenantId = tenantId;
        log.userId = userId;
        log.testItem = result.testItem();
        log.status = result.status();
        log.httpStatus = result.httpStatus();
        log.durationMillis = result.durationMillis();
        log.responseModelName = trim(responseModelName, 128);
        if ("PASSED".equals(result.status()) || "SKIPPED".equals(result.status())) {
            log.safeResponseSummary = trim(result.message(), 2000);
        } else {
            log.errorType = result.status();
            log.safeErrorDetail = trim(result.message(), 2000);
        }
        log.createdAt = Instant.now();
        return log;
    }

    private static String trim(String value, int limit) {
        return value == null ? null : value.substring(0, Math.min(limit, value.length()));
    }
}
