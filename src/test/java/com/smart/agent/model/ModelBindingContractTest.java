package com.smart.agent.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.agent.model.config.ModelCapability;
import com.smart.agent.model.config.ModelConfig;
import com.smart.agent.model.config.ModelConfigQuery;
import com.smart.agent.model.config.ModelConfigRepository;
import com.smart.agent.model.config.ModelDeploymentType;
import com.smart.agent.model.config.ModelProviderType;
import com.smart.agent.model.config.PlainTextModelSecretCipher;
import com.smart.agent.tool.ToolContext;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * 覆盖 Task 6 的模型绑定契约：
 * 运行期固定使用取得时的模型配置，配置变更不影响已持有句柄，句柄释放后回收底层客户端。
 */
class ModelBindingContractTest {
    private final InMemoryRepository repository = new InMemoryRepository();
    private final AtomicInteger closes = new AtomicInteger();
    private final ModelGatewayFactory factory = (config, apiKey) -> {
        // 使用匿名实现而非无捕获 lambda：后者的多次求值会被 JVM 复用为同一实例，
        // 使"新旧客户端不同"与"缓存复用"两类断言失去区分度。
        ModelGateway gateway = new ModelGateway() {
            @Override
            public Flux<ModelEvent> stream(ModelRequest request) {
                return Flux.empty();
            }
        };
        return new ModelGatewayFactory.CreatedGateway(gateway, closes::incrementAndGet);
    };
    private final DynamicModelRegistry registry =
            new DynamicModelRegistry(repository, new PlainTextModelSecretCipher(), factory);

    @Test
    void runKeepsUsingTheOriginalConfigEvenAfterTheModelIsUpdatedMidRun() {
        ModelConfig model = repository.add(model("glm-4.5", Set.of(ModelCapability.WEB_SEARCH)));
        ModelExecutionHandle handle = registry.acquire(model.id());
        ModelGateway pinned = handle.gateway();
        long pinnedVersion = handle.snapshot().configVersion();

        model.update("改名后的模型", ModelDeploymentType.CLOUD, "https://other.example.com/v1", "glm-4.6", null,
                Set.of(ModelCapability.STREAMING), Duration.ofSeconds(9), Duration.ofSeconds(50), 3, null);

        // 配置已推进版本，但本轮句柄仍指向取得时的网关与快照。
        assertThat(handle.gateway()).isSameAs(pinned);
        assertThat(handle.snapshot().configVersion()).isEqualTo(pinnedVersion);
        assertThat(handle.snapshot().modelName()).isEqualTo("glm-4.5");
        handle.close();
    }

    @Test
    void keepsTheCachedClientAliveForReuseAfterTheRunHandleCloses() {
        ModelConfig model = repository.add(model("glm-4.5", Set.of(ModelCapability.WEB_SEARCH)));
        ModelExecutionHandle handle = registry.acquire(model.id());
        ModelGateway pinned = handle.gateway();

        handle.close();

        // 句柄释放只归还引用计数，缓存客户端继续为后续运行复用。
        assertThat(closes).hasValue(0);
        try (ModelExecutionHandle reused = registry.acquire(model.id())) {
            assertThat(reused.gateway()).isSameAs(pinned);
        }
        assertThat(closes).hasValue(0);
    }

    @Test
    void closingAHandleTwiceDoesNotDoubleReleaseTheReference() {
        ModelConfig model = repository.add(model("glm-4.5", Set.of(ModelCapability.WEB_SEARCH)));
        ModelExecutionHandle handle = registry.acquire(model.id());

        handle.close();
        handle.close();

        // 重复释放不会破坏引用计数，后续取得仍可正常工作。
        try (ModelExecutionHandle next = registry.acquire(model.id())) {
            assertThat(next.gateway()).isNotNull();
        }
    }

    @Test
    void releasesTheClientOnceTheModelIsRetiredAndNoRunHoldsIt() {
        ModelConfig model = repository.add(model("glm-4.5", Set.of(ModelCapability.WEB_SEARCH)));
        ModelExecutionHandle handle = registry.acquire(model.id());

        // 配置变更会 retire 旧客户端，但本轮句柄仍可正常结束运行。
        model.update("新配置", ModelDeploymentType.CLOUD, "https://other.example.com/v1", "glm-4.6", null,
                Set.of(ModelCapability.WEB_SEARCH), Duration.ofSeconds(5), Duration.ofSeconds(30), 1, null);
        registry.acquire(model.id()).close();

        assertThat(closes).hasValue(0);
        handle.close();
        assertThat(closes).hasValue(1);
    }

    @Test
    void buildsAToolBindingWithoutExposingTheApiKey() {
        ModelConfig model = repository.add(model("glm-4.5", Set.of(ModelCapability.WEB_SEARCH)));
        try (ModelExecutionHandle handle = registry.acquire(model.id())) {
            ToolContext.ModelBinding binding = registry.bindingFor(handle.snapshot());

            assertThat(binding).isNotNull();
            assertThat(binding.modelId()).isEqualTo(model.id());
            assertThat(binding.modelName()).isEqualTo("glm-4.5");
            assertThat(binding.baseUrl()).isEqualTo("https://open.bigmodel.cn/api/paas/v4");
            assertThat(binding.supportsWebSearch()).isTrue();
        }
    }

    @Test
    void toolBindingReportsMissingWebSearchCapability() {
        ModelConfig model = repository.add(model("glm-4.5", Set.of(ModelCapability.TOOL_CALLING)));
        try (ModelExecutionHandle handle = registry.acquire(model.id())) {
            ToolContext.ModelBinding binding = registry.bindingFor(handle.snapshot());

            assertThat(binding.supportsWebSearch()).isFalse();
        }
    }

    @Test
    void exposesCredentialsOnlyForAnEnabledModel() {
        ModelConfig model = repository.add(model("glm-4.5", Set.of(ModelCapability.WEB_SEARCH)));

        assertThat(registry.credentialsFor(model.id())).isNotNull();

        model.setEnabled(false);
        assertThat(registry.credentialsFor(model.id())).isNull();
        assertThat(registry.credentialsFor("missing")).isNull();
    }

    @Test
    void bindingIsNullWhenThereIsNoSnapshot() {
        assertThat(registry.bindingFor(null)).isNull();
    }

    private static ModelConfig model(String modelName, Set<ModelCapability> capabilities) {
        return ModelConfig.create("智谱GLM", ModelProviderType.OPENAI_COMPATIBLE, ModelDeploymentType.CLOUD,
                "https://open.bigmodel.cn/api/paas/v4", modelName, "sk-plain-text-key", capabilities,
                Duration.ofSeconds(5), Duration.ofSeconds(30), 1, null);
    }

    private static final class InMemoryRepository implements ModelConfigRepository {
        private final Map<String, ModelConfig> values = new LinkedHashMap<>();

        ModelConfig add(ModelConfig model) {
            values.put(model.id(), model);
            return model;
        }

        @Override public ModelConfig save(ModelConfig model) { return add(model); }
        @Override public Optional<ModelConfig> findById(String id) { return Optional.ofNullable(values.get(id)); }
        @Override public Optional<ModelConfig> findDefault() {
            return values.values().stream().filter(ModelConfig::defaultModel).findFirst();
        }
        @Override public List<ModelConfig> findEnabled() {
            return values.values().stream().filter(ModelConfig::enabled).toList();
        }
        @Override public List<ModelConfig> findAllActive() { return List.copyOf(values.values()); }
        @Override public List<ModelConfig> findPage(ModelConfigQuery query) { return List.copyOf(values.values()); }
        @Override public long count(ModelConfigQuery query) { return values.size(); }
        @Override public long countReferences(String modelId) { return 0; }
        @Override public void flush() { }
    }
}
