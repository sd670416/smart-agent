package com.smart.agent.model;

import com.smart.agent.model.config.ModelCapability;
import com.smart.agent.model.config.ModelDeploymentType;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** 单次运行持有的模型网关及不可变配置快照。 */
public final class ModelExecutionHandle implements AutoCloseable {
    private final ModelGateway gateway;
    private final Snapshot snapshot;
    private final Runnable release;
    private final AtomicBoolean closed = new AtomicBoolean();

    ModelExecutionHandle(ModelGateway gateway, Snapshot snapshot, Runnable release) {
        this.gateway = gateway;
        this.snapshot = snapshot;
        this.release = release;
    }

    public ModelGateway gateway() {
        if (closed.get()) throw new IllegalStateException("模型执行句柄已经释放");
        return gateway;
    }

    public Snapshot snapshot() {
        return snapshot;
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
