package com.smart.agent.model.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smart.agent.common.api.ApiError;
import com.smart.agent.knowledge.manage.PageResult;
import com.smart.agent.security.AgentUserContext;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型配置管理接口。
 *
 * <p>所有响应均脱敏：只返回密钥存在标记和掩码，不返回明文或密文。</p>
 */
@RestController
@RequestMapping("/agent/models")
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ModelConfigController {
    private static final String CONTEXT = "com.smart.agent.security.AgentUserContext";

    private final ModelConfigService service;
    private final ModelCompatibilityTester tester;

    public ModelConfigController(ModelConfigService service) {
        this(service, null);
    }

    @Autowired
    public ModelConfigController(ModelConfigService service, ModelCompatibilityTester tester) {
        this.service = service;
        this.tester = tester;
    }

    /** 已启用模型列表，供聊天页模型选择器使用。 */
    @GetMapping("/enabled")
    public List<ModelConfigResponse.Summary> enabled() {
        return service.enabledModels().stream().map(ModelConfigController::summary).toList();
    }

    @GetMapping("/page")
    public PageResult<ModelConfigResponse> page(@RequestParam(required = false) String name,
            @RequestParam(required = false) ModelDeploymentType deploymentType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean defaultModel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(CONTEXT) AgentUserContext actor) {
        PageResult<ModelConfig> result = service.page(
                new ModelConfigQuery(name, deploymentType, enabled, defaultModel, page, size), actor);
        return new PageResult<>(result.items().stream().map(config -> response(config, null)).toList(),
                result.total(), result.page(), result.size());
    }

    @GetMapping("/{id}")
    public ModelConfigResponse detail(@PathVariable String id, @RequestAttribute(CONTEXT) AgentUserContext actor) {
        ModelConfig config = service.detail(id, actor);
        return response(config, service.apiKeyMask(config));
    }

    @PostMapping
    public ModelConfigResponse create(@RequestBody ModelConfigRequest request,
            @RequestAttribute(CONTEXT) AgentUserContext actor) {
        ModelConfig config = service.create(request, actor);
        return response(config, service.apiKeyMask(config));
    }

    @PutMapping("/{id}")
    public ModelConfigResponse update(@PathVariable String id, @RequestBody ModelConfigRequest request,
            @RequestAttribute(CONTEXT) AgentUserContext actor) {
        ModelConfig config = service.update(id, request, actor);
        return response(config, service.apiKeyMask(config));
    }

    @PutMapping("/{id}/enabled")
    public ModelConfigResponse setEnabled(@PathVariable String id, @RequestBody EnabledRequest request,
            @RequestAttribute(CONTEXT) AgentUserContext actor) {
        if (request == null || request.enabled() == null) {
            throw new IllegalArgumentException("启用状态不能为空");
        }
        ModelConfig config = service.setEnabled(id, request.enabled(), actor);
        return response(config, null);
    }

    @PutMapping("/{id}/default")
    public ModelConfigResponse setDefault(@PathVariable String id, @RequestAttribute(CONTEXT) AgentUserContext actor) {
        ModelConfig config = service.setDefault(id, actor);
        return response(config, null);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, @RequestAttribute(CONTEXT) AgentUserContext actor) {
        service.delete(id, actor);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    public ModelTestResult test(@PathVariable String id, @RequestAttribute(CONTEXT) AgentUserContext actor) {
        if (tester == null) {
            throw new IllegalStateException("模型测试服务尚未启用");
        }
        ModelConfig config = service.testTarget(id, actor);
        return tester.test(config, actor);
    }

    @ExceptionHandler(ModelConfigNotFoundException.class)
    ResponseEntity<ApiError> notFound(ModelConfigNotFoundException exception) {
        return ResponseEntity.status(404).body(new ApiError("MODEL_CONFIG_NOT_FOUND", exception.getMessage(), null));
    }

    @ExceptionHandler(ModelConfigForbiddenException.class)
    ResponseEntity<ApiError> forbidden(ModelConfigForbiddenException exception) {
        return ResponseEntity.status(403).body(new ApiError("MODEL_CONFIG_FORBIDDEN", exception.getMessage(), null));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> conflict(IllegalStateException exception) {
        return ResponseEntity.status(409).body(new ApiError("MODEL_CONFIG_STATE_CONFLICT", exception.getMessage(), null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiError("MODEL_CONFIG_INVALID", exception.getMessage(), null));
    }

    @ExceptionHandler(ModelSecretException.class)
    ResponseEntity<ApiError> secretUnavailable(ModelSecretException exception) {
        return ResponseEntity.status(500).body(new ApiError("MODEL_CONFIG_SECRET_UNAVAILABLE", exception.getMessage(), null));
    }

    private static ModelConfigResponse response(ModelConfig config, String apiKeyMask) {
        return new ModelConfigResponse(config.id(), config.name(), config.providerType(), config.deploymentType(),
                config.baseUrl(), config.modelName(), config.encryptedApiKey() != null, apiKeyMask,
                config.capabilities(), (int) config.connectTimeout().toSeconds(),
                (int) config.readTimeout().toSeconds(), config.enabled(), config.defaultModel(), config.sort(),
                config.remarks(), config.configVersion(), config.lastTestStatus(), config.lastTestTime(),
                config.lastTestSummary(), config.createdAt(), config.updatedAt());
    }

    private static ModelConfigResponse.Summary summary(ModelConfig config) {
        return new ModelConfigResponse.Summary(config.id(), config.name(), config.deploymentType(),
                config.capabilities(), config.defaultModel());
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record EnabledRequest(Boolean enabled) {
    }
}
