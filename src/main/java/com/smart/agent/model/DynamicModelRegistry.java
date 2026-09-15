package com.smart.agent.model;

import com.smart.agent.model.config.ModelConfig;
import com.smart.agent.model.config.ModelConfigRepository;
import com.smart.agent.model.config.ModelSecretCipher;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 按模型 ID 与配置版本缓存客户端，并为每次运行提供引用计数句柄。 */
@Component
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DynamicModelRegistry implements ModelCredentialSource {
    private final ModelConfigRepository repository;
    private final ModelSecretCipher cipher;
    private final ModelGatewayFactory factory;
    private final Map<String, SharedGateway> cache = new ConcurrentHashMap<>();

    public DynamicModelRegistry(ModelConfigRepository repository, ModelSecretCipher cipher, ModelGatewayFactory factory) {
        this.repository = repository;
        this.cipher = cipher;
        this.factory = factory;
    }

    public ModelExecutionHandle acquire(String modelId) {
        ModelConfig config = repository.findById(modelId).orElse(null);
        if (config == null) {
            evict(modelId);
            throw new IllegalArgumentException("模型不存在或已删除");
        }
        if (!config.enabled()) {
            evict(modelId);
            throw new IllegalStateException("该模型已停用，请选择其他模型");
        }
        SharedGateway shared = cache.compute(modelId, (id, current) -> current != null
                && current.version == config.configVersion() ? current : replace(current, config));
        return shared.acquire();
    }

    private void evict(String modelId) {
        SharedGateway removed = cache.remove(modelId);
        if (removed != null) removed.retire();
    }

    public ModelExecutionHandle acquireDefault() {
        ModelConfig config = repository.findDefault()
                .orElseThrow(() -> new IllegalStateException("系统尚未设置默认模型，请联系管理员配置"));
        return acquire(config.id());
    }

    /**
     * 按模型 ID 换取连接信息，供联网搜索等需要直接发起 HTTP 请求的能力使用。
     * 仅返回必要字段，绝不回传配置版本与能力之外的内部信息。
     */
    @Override
    public Credentials credentialsFor(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return null;
        }
        ModelConfig config = repository.findById(modelId).orElse(null);
        if (config == null || !config.enabled()) {
            return null;
        }
        return new Credentials(config.baseUrl(), cipher.decrypt(config.encryptedApiKey()), config.modelName());
    }

    /**
     * 依据执行句柄快照组装工具侧可见的模型绑定。
     *
     * <p>只暴露模型标识、展示名、实际模型名、连接地址与能力集合；
     * API Key 不进入绑定，需要发请求的能力通过 {@link #credentialsFor(String)} 按需换取。
     *
     * @return 组装结果；快照为空或模型已不可用时返回 {@code null}
     */
    public com.smart.agent.tool.ToolContext.ModelBinding bindingFor(ModelExecutionHandle.Snapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        Credentials credentials = credentialsFor(snapshot.modelId());
        String baseUrl = credentials == null ? null : credentials.baseUrl();
        return new com.smart.agent.tool.ToolContext.ModelBinding(snapshot.modelId(), snapshot.displayName(),
                snapshot.modelName(), baseUrl, snapshot.capabilities());
    }

    private SharedGateway replace(SharedGateway previous, ModelConfig config) {
        String apiKey = cipher.decrypt(config.encryptedApiKey());
        ModelGatewayFactory.CreatedGateway created = factory.create(config, apiKey);
        ModelExecutionHandle.Snapshot snapshot = new ModelExecutionHandle.Snapshot(
                config.id(), config.configVersion(), config.name(), config.modelName(), config.deploymentType(),
                config.capabilities(), config.connectTimeout(), config.readTimeout());
        SharedGateway replacement = new SharedGateway(
                config.configVersion(), created.gateway(), snapshot, created.closeAction());
        if (previous != null) previous.retire();
        return replacement;
    }

    @PreDestroy
    void closeAll() {
        for (SharedGateway gateway : new ArrayList<>(cache.values())) gateway.retire();
        cache.clear();
    }

    private static final class SharedGateway {
        private final long version;
        private final ModelGateway gateway;
        private final ModelExecutionHandle.Snapshot snapshot;
        private final Runnable closeAction;
        private final AtomicInteger references = new AtomicInteger();
        private final AtomicBoolean retired = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();

        private SharedGateway(long version, ModelGateway gateway, ModelExecutionHandle.Snapshot snapshot,
                Runnable closeAction) {
            this.version = version;
            this.gateway = gateway;
            this.snapshot = snapshot;
            this.closeAction = closeAction;
        }

        private ModelExecutionHandle acquire() {
            references.incrementAndGet();
            if (retired.get()) {
                release();
                throw new IllegalStateException("模型配置正在更新，请重试");
            }
            return new ModelExecutionHandle(gateway, snapshot, this::release);
        }

        private void retire() {
            retired.set(true);
            closeIfUnused();
        }

        private void release() {
            if (references.decrementAndGet() < 0) throw new IllegalStateException("模型执行句柄重复释放");
            closeIfUnused();
        }

        private void closeIfUnused() {
            if (retired.get() && references.get() == 0 && closed.compareAndSet(false, true)) closeAction.run();
        }
    }
}
