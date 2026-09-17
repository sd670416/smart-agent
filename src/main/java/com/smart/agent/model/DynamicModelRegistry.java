package com.smart.agent.model;

import com.smart.agent.model.config.ModelConfig;
import com.smart.agent.model.config.ModelConfigRepository;
import com.smart.agent.model.config.ModelSecretCipher;
import com.smart.agent.tool.ToolContext;
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

    public synchronized ModelExecutionHandle acquire(String modelId) {
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
     * 按模型 ID 换取连接信息，读取的是数据库里<em>当前最新</em>的配置。
     *
     * <p><b>一次运行内不要调用它</b>——配置中途变化会让调用方拿到与运行快照不同的版本。
     * 运行期请使用 {@link ModelExecutionHandle#credentials()} 并经 {@link ModelRunContext} 传递；
     * 本方法只服务于没有运行上下文的兜底路径。
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
     * 依据执行句柄组装工具侧可见的模型绑定。
     *
     * <p>只暴露模型标识、展示名、实际模型名、连接地址与能力集合；
     * 连接地址取自句柄持有的<em>同一配置版本</em>，因此一轮运行内聊天与工具的连接信息必然一致。
     * API Key 不进入绑定，需要发请求的能力经 {@link ModelRunContext} 从运行期私有上下文读取。
     *
     * @return 组装结果；句柄为空时返回 {@code null}
     */
    public ToolContext.ModelBinding bindingFor(ModelExecutionHandle handle) {
        if (handle == null) {
            return null;
        }
        return bindingOf(handle.snapshot(), handle.credentials());
    }

    /**
     * 仅凭快照组装绑定，保留给只持有快照的调用方。
     *
     * <p>连接地址只在快照版本仍是缓存中的当前版本时给出；版本已推进时返回不含地址的绑定，
     * 绝不把新版本地址混进正在执行的旧运行。
     */
    public ToolContext.ModelBinding bindingFor(ModelExecutionHandle.Snapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return bindingOf(snapshot, credentialsForVersion(snapshot.modelId(), snapshot.configVersion()));
    }

    /** 按版本取缓存中的凭据：版本不匹配一律视为不可用，避免混用新旧配置。 */
    private Credentials credentialsForVersion(String modelId, long version) {
        SharedGateway shared = cache.get(modelId);
        return shared != null && shared.version == version ? shared.credentials : null;
    }

    private static ToolContext.ModelBinding bindingOf(
            ModelExecutionHandle.Snapshot snapshot, Credentials credentials) {
        String baseUrl = credentials == null ? null : credentials.baseUrl();
        return new ToolContext.ModelBinding(snapshot.modelId(), snapshot.displayName(),
                snapshot.modelName(), baseUrl, snapshot.capabilities());
    }

    private SharedGateway replace(SharedGateway previous, ModelConfig config) {
        String apiKey = cipher.decrypt(config.encryptedApiKey());
        ModelGatewayFactory.CreatedGateway created = factory.create(config, apiKey);
        ModelExecutionHandle.Snapshot snapshot = new ModelExecutionHandle.Snapshot(
                config.id(), config.configVersion(), config.name(), config.modelName(), config.deploymentType(),
                config.capabilities(), config.connectTimeout(), config.readTimeout());
        Credentials credentials = new Credentials(config.baseUrl(), apiKey, config.modelName());
        SharedGateway replacement = new SharedGateway(
                config.configVersion(), created.gateway(), snapshot, credentials, created.closeAction());
        if (previous != null) previous.retire();
        return replacement;
    }

    @PreDestroy
    synchronized void closeAll() {
        for (SharedGateway gateway : new ArrayList<>(cache.values())) gateway.retire();
        cache.clear();
    }

    private static final class SharedGateway {
        private final long version;
        private final ModelGateway gateway;
        private final ModelExecutionHandle.Snapshot snapshot;
        private final Credentials credentials;
        private final Runnable closeAction;
        private final AtomicInteger references = new AtomicInteger();
        private final AtomicBoolean retired = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();

        private SharedGateway(long version, ModelGateway gateway, ModelExecutionHandle.Snapshot snapshot,
                Credentials credentials, Runnable closeAction) {
            this.version = version;
            this.gateway = gateway;
            this.snapshot = snapshot;
            this.credentials = credentials;
            this.closeAction = closeAction;
        }

        private ModelExecutionHandle acquire() {
            references.incrementAndGet();
            if (retired.get()) {
                release();
                throw new IllegalStateException("模型配置正在更新，请重试");
            }
            return new ModelExecutionHandle(gateway, snapshot, credentials, this::release);
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
