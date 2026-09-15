package com.smart.agent.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.agent.model.config.ModelCapability;
import com.smart.agent.model.config.ModelConfig;
import com.smart.agent.model.config.ModelConfigQuery;
import com.smart.agent.model.config.ModelConfigRepository;
import com.smart.agent.model.config.ModelDeploymentType;
import com.smart.agent.model.config.ModelProviderType;
import com.smart.agent.model.config.PlainTextModelSecretCipher;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class DynamicModelRegistryTest {
    private final InMemoryRepository repository = new InMemoryRepository();
    private final AtomicInteger creations = new AtomicInteger();
    private final AtomicInteger closes = new AtomicInteger();
    private final ModelGatewayFactory factory = (config, apiKey) -> {
        creations.incrementAndGet();
        // 每次创建都返回新的匿名实现：无捕获 lambda 会被 JVM 复用同一实例，
        // 用它会让"新旧客户端不同"的断言失去意义。
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
    void reusesClientForSameModelVersion() {
        ModelConfig model = repository.add(model());

        try (ModelExecutionHandle first = registry.acquire(model.id());
                ModelExecutionHandle second = registry.acquire(model.id())) {
            assertThat(first.gateway()).isSameAs(second.gateway());
            assertThat(first.snapshot().configVersion()).isEqualTo(1);
            assertThat(first.snapshot().capabilities()).containsExactly(ModelCapability.STREAMING);
        }

        assertThat(creations).hasValue(1);
        assertThat(closes).hasValue(0);
    }

    @Test
    void createsNewClientAfterConfigVersionChangesAndClosesOldOneAfterLastHandleReleases() {
        ModelConfig model = repository.add(model());
        ModelExecutionHandle oldHandle = registry.acquire(model.id());
        model.update("新名称", ModelDeploymentType.CLOUD, "https://new.example.com/v1", "new-model", null,
                Set.of(ModelCapability.STREAMING), Duration.ofSeconds(6), Duration.ofSeconds(40), 2, null);

        ModelExecutionHandle newHandle = registry.acquire(model.id());

        assertThat(newHandle.gateway()).isNotSameAs(oldHandle.gateway());
        assertThat(newHandle.snapshot().configVersion()).isEqualTo(2);
        assertThat(creations).hasValue(2);
        assertThat(closes).hasValue(0);
        oldHandle.close();
        assertThat(closes).hasValue(1);
        newHandle.close();
    }

    @Test
    void rejectsDisabledModelAndMissingDefault() {
        ModelConfig model = repository.add(model());
        model.setEnabled(false);

        assertThatThrownBy(() -> registry.acquire(model.id()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("停用");
        assertThatThrownBy(registry::acquireDefault)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("默认模型");
        assertThat(creations).hasValue(0);
    }

    @Test
    void retiresCachedClientWhenModelIsDisabled() {
        ModelConfig model = repository.add(model());
        ModelExecutionHandle active = registry.acquire(model.id());
        model.setEnabled(false);

        assertThatThrownBy(() -> registry.acquire(model.id())).hasMessageContaining("停用");
        assertThat(closes).hasValue(0);
        active.close();
        assertThat(closes).hasValue(1);
    }

    @Test
    void resolvesConfiguredDefaultModel() {
        ModelConfig model = repository.add(model());
        model.makeDefault();

        try (ModelExecutionHandle handle = registry.acquireDefault()) {
            assertThat(handle.snapshot().modelId()).isEqualTo(model.id());
        }
    }

    private static ModelConfig model() {
        return ModelConfig.create("模型", ModelProviderType.OPENAI_COMPATIBLE, ModelDeploymentType.CLOUD,
                "https://api.example.com/v1", "model-v1", "sk-test", Set.of(ModelCapability.STREAMING),
                Duration.ofSeconds(5), Duration.ofSeconds(30), 1, null);
    }

    private static final class InMemoryRepository implements ModelConfigRepository {
        private final Map<String, ModelConfig> values = new LinkedHashMap<>();

        ModelConfig add(ModelConfig model) { values.put(model.id(), model); return model; }
        @Override public ModelConfig save(ModelConfig model) { return add(model); }
        @Override public Optional<ModelConfig> findById(String id) { return Optional.ofNullable(values.get(id)); }
        @Override public Optional<ModelConfig> findDefault() {
            return values.values().stream().filter(ModelConfig::defaultModel).findFirst();
        }
        @Override public List<ModelConfig> findEnabled() { return values.values().stream().filter(ModelConfig::enabled).toList(); }
        @Override public List<ModelConfig> findAllActive() { return List.copyOf(values.values()); }
        @Override public List<ModelConfig> findPage(ModelConfigQuery query) { return List.copyOf(values.values()); }
        @Override public long count(ModelConfigQuery query) { return values.size(); }
        @Override public long countReferences(String modelId) { return 0; }
        @Override public void flush() { }
    }
}
