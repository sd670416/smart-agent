package com.smart.agent.model.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.smart.agent.model.ModelGatewayProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

/** YAML 引导导入的幂等、不覆盖与失败容错契约。 */
class ModelConfigBootstrapTest {

    private final InMemoryModelConfigRepository repository = new InMemoryModelConfigRepository();
    private final ModelConfigService service =
            new ModelConfigService(repository, new PlainTextModelSecretCipher());

    @Test
    void importsTheYamlModelAsTheDefaultWhenNoConfigurationExists() {
        bootstrap(true, "TOOL_CALLING,STREAMING").run(null);

        assertThat(repository.findAllActive()).hasSize(1);
        ModelConfig imported = repository.findDefault().orElseThrow();
        assertThat(imported.modelName()).isEqualTo("local-test-model");
        assertThat(imported.name()).isEqualTo("local-test-model（YAML 引导）");
        // 明文策略下 API Key 直接落库，但仍不出现在接口与日志中。
        assertThat(imported.encryptedApiKey()).isEqualTo("local-development-key");
        assertThat(imported.enabled()).isTrue();
        assertThat(imported.defaultModel()).isTrue();
        assertThat(imported.configVersion()).isEqualTo(1L);
        assertThat(imported.capabilities())
                .containsExactlyInAnyOrder(ModelCapability.TOOL_CALLING, ModelCapability.STREAMING);
        // 本地 http 地址必须按本地部署导入，否则会被「云端必须 https」校验拒绝。
        assertThat(imported.deploymentType()).isEqualTo(ModelDeploymentType.LOCAL);
    }

    @Test
    void treatsHttpsEndpointAsCloudDeployment() {
        bootstrap(true, "TOOL_CALLING").withYaml(new ModelGatewayProperties("openai-compatible",
                "https://api.example.com/v1", "sk-cloud", "gpt-4o", null, null)).run(null);

        ModelConfig imported = repository.findDefault().orElseThrow();
        assertThat(imported.deploymentType()).isEqualTo(ModelDeploymentType.CLOUD);
        assertThat(imported.encryptedApiKey()).isEqualTo("sk-cloud");
    }

    @Test
    void runningTwiceKeepsExactlyOneImportedModel() {
        BootstrapRunner bootstrap = bootstrap(true, "TOOL_CALLING,STREAMING");

        bootstrap.run(null);
        bootstrap.run(null);

        assertThat(repository.findAllActive()).hasSize(1);
    }

    @Test
    void keepsExistingRecordsAndNeverOverwritesOrRepointsTheDefault() {
        ModelConfig existing = ModelConfig.create("已有模型", ModelProviderType.OPENAI_COMPATIBLE,
                ModelDeploymentType.LOCAL, "http://127.0.0.1:11434/v1", "existing-model", "existing-key",
                Set.of(ModelCapability.TOOL_CALLING), Duration.ofSeconds(5), Duration.ofSeconds(30), 0, null);
        repository.save(existing);

        bootstrap(true, "TOOL_CALLING,STREAMING").run(null);

        assertThat(repository.findAllActive()).hasSize(1);
        ModelConfig kept = repository.findById(existing.id()).orElseThrow();
        assertThat(kept.modelName()).isEqualTo("existing-model");
        assertThat(kept.encryptedApiKey()).isEqualTo("existing-key");
        assertThat(kept.defaultModel()).isFalse();
        assertThat(repository.findDefault()).isEmpty();
    }

    @Test
    void skipsImportEntirelyWhenBootstrapIsDisabled() {
        bootstrap(false, "TOOL_CALLING").run(null);

        assertThat(repository.findAllActive()).isEmpty();
    }

    @Test
    void doesNotBlockStartupWhenTheYamlModelFailsValidation() {
        // 不支持的协议地址会在保存校验阶段被拒绝；引导必须吞掉异常，让应用照常启动并沿用数据库配置。
        // 注意 ModelGatewayProperties 会给 apiKey 兜底，所以「云端缺密钥」在这里不会触发，只能靠地址校验。
        BootstrapRunner broken = bootstrap(true, "TOOL_CALLING")
                .withYaml(new ModelGatewayProperties("openai-compatible", "ftp://api.example.com/v1",
                        "sk-cloud", "gpt-4o", null, null));

        assertThatCode(() -> broken.run(null)).doesNotThrowAnyException();

        assertThat(repository.findAllActive()).isEmpty();
    }

    @Test
    void ignoresUnknownCapabilityTokensAndRoundsSubSecondTimeoutsUp() {
        bootstrap(true, "TOOL_CALLING, not-a-capability ,").run(null);

        ModelConfig imported = repository.findDefault().orElseThrow();
        assertThat(imported.capabilities()).containsExactly(ModelCapability.TOOL_CALLING);
    }

    @Test
    void roundsSubSecondTimeoutsUpToWholeSeconds() {
        BootstrapRunner bootstrap = bootstrap(true, "TOOL_CALLING")
                .withYaml(new ModelGatewayProperties("local", "http://localhost:11434/v1", "local-key",
                        "local-test-model", Duration.ofMillis(200), Duration.ofMillis(500)));

        bootstrap.run(null);

        ModelConfig imported = repository.findDefault().orElseThrow();
        assertThat(imported.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(imported.readTimeout()).isEqualTo(Duration.ofSeconds(1));
    }

    private BootstrapRunner bootstrap(boolean enabled, String capabilities) {
        return new BootstrapRunner(enabled, capabilities);
    }

    /** 每次 run 都重新构造引导组件，用于模拟「应用重启」。 */
    private final class BootstrapRunner {
        private final boolean enabled;
        private final String capabilities;
        private ModelGatewayProperties yaml = new ModelGatewayProperties("local",
                "http://localhost:11434/v1", "local-development-key", "local-test-model",
                Duration.ofSeconds(5), Duration.ofSeconds(30));

        private BootstrapRunner(boolean enabled, String capabilities) {
            this.enabled = enabled;
            this.capabilities = capabilities;
        }

        BootstrapRunner withYaml(ModelGatewayProperties yaml) {
            this.yaml = yaml;
            return this;
        }

        void run(ApplicationArguments args) {
            new ModelConfigBootstrap(service, yaml, new ModelBootstrapProperties(enabled, capabilities)).run(args);
        }
    }

    /** 仅覆盖服务层规则的内存仓储，不模拟 JPA 分页 SQL。 */
    private static final class InMemoryModelConfigRepository implements ModelConfigRepository {
        private final Map<String, ModelConfig> store = new LinkedHashMap<>();

        @Override public ModelConfig save(ModelConfig config) {
            store.put(config.id(), config);
            return config;
        }

        @Override public Optional<ModelConfig> findById(String id) {
            ModelConfig config = store.get(id);
            return config == null || config.deleted() ? Optional.empty() : Optional.of(config);
        }

        @Override public Optional<ModelConfig> findDefault() {
            return store.values().stream().filter(config -> !config.deleted() && config.defaultModel()).findFirst();
        }

        @Override public List<ModelConfig> findEnabled() {
            return store.values().stream().filter(config -> !config.deleted() && config.enabled()).toList();
        }

        @Override public List<ModelConfig> findAllActive() {
            return store.values().stream().filter(config -> !config.deleted()).toList();
        }

        @Override public List<ModelConfig> findPage(ModelConfigQuery query) {
            return new ArrayList<>(store.values());
        }

        @Override public long count(ModelConfigQuery query) {
            return store.size();
        }

        @Override public long countReferences(String modelId) {
            return 0L;
        }

        @Override public void flush() {
        }
    }
}
