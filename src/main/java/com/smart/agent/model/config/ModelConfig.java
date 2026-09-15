package com.smart.agent.model.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "ai_model_config")
public class ModelConfig {
    @Id @Column(length = 36, nullable = false, updatable = false) private String id;
    @Column(nullable = false, length = 128) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "provider_type", nullable = false, length = 32)
    private ModelProviderType providerType;
    @Enumerated(EnumType.STRING) @Column(name = "deployment_type", nullable = false, length = 16)
    private ModelDeploymentType deploymentType;
    @Column(name = "base_url", nullable = false, length = 512) private String baseUrl;
    @Column(name = "model_name", nullable = false, length = 128) private String modelName;
    @Column(name = "encrypted_api_key", columnDefinition = "LONGTEXT") private String encryptedApiKey;
    @Column(name = "capabilities", nullable = false, length = 512) private String capabilityValues;
    @Column(name = "connect_timeout_seconds", nullable = false) private int connectTimeoutSeconds;
    @Column(name = "read_timeout_seconds", nullable = false) private int readTimeoutSeconds;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "is_default", nullable = false) private boolean defaultModel;
    @Column(nullable = false) private int sort;
    @Column(length = 1000) private String remarks;
    @Column(name = "config_version", nullable = false) private long configVersion;
    @Column(name = "last_test_status", length = 32) private String lastTestStatus;
    @Column(name = "last_test_time") private Instant lastTestTime;
    @Column(name = "last_test_summary", length = 2000) private String lastTestSummary;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "create_by", length = 36) private String createdBy;
    @Column(name = "update_by", length = 36) private String updatedBy;
    @Column(name = "create_time", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "update_time", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private Long version;

    protected ModelConfig() {}

    public static ModelConfig create(String name, ModelProviderType providerType,
            ModelDeploymentType deploymentType, String baseUrl, String modelName, String encryptedApiKey,
            Set<ModelCapability> capabilities, Duration connectTimeout, Duration readTimeout, int sort,
            String remarks) {
        ModelConfig config = new ModelConfig();
        config.id = UUID.randomUUID().toString();
        config.providerType = java.util.Objects.requireNonNull(providerType, "providerType");
        config.enabled = true;
        config.configVersion = 1L;
        config.apply(name, deploymentType, baseUrl, modelName, encryptedApiKey, capabilities,
                connectTimeout, readTimeout, sort, remarks);
        return config;
    }

    public void update(String name, ModelDeploymentType deploymentType, String baseUrl, String modelName,
            String encryptedApiKey, Set<ModelCapability> capabilities, Duration connectTimeout,
            Duration readTimeout, int sort, String remarks) {
        apply(name, deploymentType, baseUrl, modelName,
                encryptedApiKey == null ? this.encryptedApiKey : encryptedApiKey,
                capabilities, connectTimeout, readTimeout, sort, remarks);
        configVersion++;
    }

    private void apply(String name, ModelDeploymentType deploymentType, String baseUrl, String modelName,
            String encryptedApiKey, Set<ModelCapability> capabilities, Duration connectTimeout,
            Duration readTimeout, int sort, String remarks) {
        this.name = requireText(name, "name");
        this.deploymentType = java.util.Objects.requireNonNull(deploymentType, "deploymentType");
        this.baseUrl = requireText(baseUrl, "baseUrl");
        this.modelName = requireText(modelName, "modelName");
        this.encryptedApiKey = encryptedApiKey;
        this.capabilityValues = encodeCapabilities(capabilities);
        this.connectTimeoutSeconds = positiveSeconds(connectTimeout, "connectTimeout");
        this.readTimeoutSeconds = positiveSeconds(readTimeout, "readTimeout");
        this.sort = sort;
        this.remarks = remarks;
    }

    public void setEnabled(boolean enabled) {
        if (!enabled && defaultModel) throw new IllegalStateException("default model must remain enabled");
        if (this.enabled != enabled) {
            this.enabled = enabled;
            // 启停改变运行期可用性，必须推进配置版本，使动态模型缓存立即失效
            configVersion++;
        }
    }

    public void makeDefault() {
        if (!enabled || deleted) throw new IllegalStateException("default model must be enabled");
        defaultModel = true;
    }

    public void clearDefault() { defaultModel = false; }

    /**
     * 记录创建人；创建人写入同时兼作首次修改人，避免审计字段留空。
     */
    public void markCreatedBy(String operatorId) {
        this.createdBy = operatorId;
        if (this.updatedBy == null) {
            this.updatedBy = operatorId;
        }
    }

    /** 记录修改人。 */
    public void markUpdatedBy(String operatorId) {
        this.updatedBy = operatorId;
    }

    public void delete() {
        if (defaultModel) throw new IllegalStateException("default model cannot be deleted");
        deleted = true;
        enabled = false;
    }

    public void recordTest(String status, String summary) {
        lastTestStatus = status;
        lastTestSummary = summary == null ? null : summary.substring(0, Math.min(2000, summary.length()));
        lastTestTime = Instant.now();
    }

    public Set<ModelCapability> capabilities() {
        if (capabilityValues == null || capabilityValues.isBlank()) return Collections.emptySet();
        return Collections.unmodifiableSet(Arrays.stream(capabilityValues.split(","))
                .map(ModelCapability::valueOf).collect(Collectors.toCollection(() -> EnumSet.noneOf(ModelCapability.class))));
    }

    private static String encodeCapabilities(Set<ModelCapability> values) {
        if (values == null || values.isEmpty()) return "";
        return values.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    private static int positiveSeconds(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative())
            throw new IllegalArgumentException(name + " must be a positive duration of at least 1 second");
        if (value.getNano() != 0)
            throw new IllegalArgumentException(name + " must use whole seconds, no sub-second precision allowed");
        if (value.getSeconds() > Integer.MAX_VALUE)
            throw new IllegalArgumentException(name + " exceeds the supported range");
        return (int) value.getSeconds();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    @PrePersist void initializeTimestamps() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }

    public String id() { return id; }
    public String name() { return name; }
    public ModelProviderType providerType() { return providerType; }
    public ModelDeploymentType deploymentType() { return deploymentType; }
    public String baseUrl() { return baseUrl; }
    public String modelName() { return modelName; }
    public String encryptedApiKey() { return encryptedApiKey; }
    public Duration connectTimeout() { return Duration.ofSeconds(connectTimeoutSeconds); }
    public Duration readTimeout() { return Duration.ofSeconds(readTimeoutSeconds); }
    public boolean enabled() { return enabled; }
    public boolean defaultModel() { return defaultModel; }
    public int sort() { return sort; }
    public String remarks() { return remarks; }
    public long configVersion() { return configVersion; }
    public String lastTestStatus() { return lastTestStatus; }
    public Instant lastTestTime() { return lastTestTime; }
    public String lastTestSummary() { return lastTestSummary; }
    public boolean deleted() { return deleted; }
    public String createdBy() { return createdBy; }
    public String updatedBy() { return updatedBy; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
