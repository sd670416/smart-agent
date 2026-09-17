package com.smart.agent.model;

import com.smart.agent.model.config.ModelCapability;
import com.smart.agent.model.config.ModelDeploymentType;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** 单次运行持有的模型网关、不可变配置快照，以及同一配置版本的凭据。 */
public final class ModelExecutionHandle implements AutoCloseable {
    private final ModelGateway gateway;
    private final Snapshot snapshot;
    private final ModelCredentialSource.Credentials credentials;
    private final Runnable release;
    private final AtomicBoolean closed = new AtomicBoolean();

    ModelExecutionHandle(ModelGateway gateway, Snapshot snapshot,
            ModelCredentialSource.Credentials credentials, Runnable release) {
        this.gateway = gateway;
        this.snapshot = snapshot;
        this.credentials = credentials;
        this.release = release;
    }

    public ModelGateway gateway() {
        if (closed.get()) throw new IllegalStateException("模型执行句柄已经释放");
        return gateway;
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    /**
     * 本轮运行绑定的模型凭据，与 {@link #snapshot()} 来自同一次读取的配置，版本必然一致。
     *
     * <p>仅供联网搜索等需要直接发起 HTTP 请求的能力在运行期读取，
     * 由编排层经 {@link ModelRunContext} 传递，<b>禁止写入日志、调试事件、接口响应
     * 或任何可序列化结构</b>。
     *
     * @return 本轮凭据；模型未配置 API Key 时为 {@code null}
     */
    public ModelCredentialSource.Credentials credentials() {
        return credentials;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) release.run();
    }

    public record Snapshot(String modelId, long configVersion, String displayName, String modelName,
            ModelDeploymentType deploymentType, Set<ModelCapability> capabilities,
            Duration connectTimeout, Duration readTimeout) {
        public Snapshot {
            capabilities = Set.copyOf(capabilities);
        }
    }
}
