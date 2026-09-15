package com.smart.agent.model.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.agent.knowledge.manage.PageResult;
import com.smart.agent.security.AgentUserContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelConfigServiceTest {

    private final InMemoryModelConfigRepository repository = new InMemoryModelConfigRepository();
    private final ModelConfigService service =
            new ModelConfigService(repository, new PlainTextModelSecretCipher());
    private final AgentUserContext manager = new AgentUserContext("tenant-1", "user-1", "identity-1",
            Set.of(ModelConfigService.PERMISSION_MENU, ModelConfigService.PERMISSION_ADD,
                    ModelConfigService.PERMISSION_UPDATE, ModelConfigService.PERMISSION_DELETE,
                    ModelConfigService.PERMISSION_ENABLE, ModelConfigService.PERMISSION_SET_DEFAULT),
            Set.of());

    @Test
    void storesCloudApiKeyEncryptedAndKeepsItWhenUpdateOmitsTheKey() {
        ModelConfig created = service.create(
                request("智谱", ModelDeploymentType.CLOUD, "https://api.example.com/v1", "glm-4", "secret-value"),
                manager);
        String encrypted = created.encryptedApiKey();

        assertThat(encrypted).isEqualTo("secret-value");

        ModelConfig updated = service.update(created.id(),
                request("智谱新版", ModelDeploymentType.CLOUD, "https://api.example.com/v1", "glm-4-plus", null),
                manager);

        assertThat(updated.encryptedApiKey()).isEqualTo(encrypted);
        assertThat(updated.modelName()).isEqualTo("glm-4-plus");
        assertThat(updated.configVersion()).isEqualTo(2L);
    }

    @Test
    void permitsLocalModelWithoutApiKeyAndRejectsCloudModelWithoutOne() {
        ModelConfig local = service.create(
                request("本地", ModelDeploymentType.LOCAL, "http://127.0.0.1:11434/v1", "local", null), manager);

        assertThat(local.encryptedApiKey()).isNull();
        assertThat(local.enabled()).isTrue();

        assertThatThrownBy(() -> service.create(
                request("云端无密钥", ModelDeploymentType.CLOUD, "https://api.example.com/v1", "glm", null), manager))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API Key");
    }

    @Test
    void requiresHttpsForCloudEndpointsAndBlocksMetadataAddresses() {
        assertThatThrownBy(() -> service.create(
                request("明文云端", ModelDeploymentType.CLOUD, "http://api.example.com/v1", "glm", "key"), manager))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");

        assertThatThrownBy(() -> service.create(
                request("元数据地址", ModelDeploymentType.LOCAL, "http://169.254.169.254/latest", "x", null), manager))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("元数据");
    }

    @Test
    void refusesDisablingOrDeletingTheDefaultModel() {
        ModelConfig created = service.create(
                request("默认", ModelDeploymentType.CLOUD, "https://api.example.com/v1", "glm-4", "key"), manager);
        service.setDefault(created.id(), manager);

        assertThatThrownBy(() -> service.setEnabled(created.id(), false, manager))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default model");
        assertThatThrownBy(() -> service.delete(created.id(), manager))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default model");
    }

    @Test
    void releasesPreviousDefaultBeforeAssigningTheNewOne() {
        ModelConfig first = service.create(
                request("甲", ModelDeploymentType.CLOUD, "https://a.example.com/v1", "a", "key-a"), manager);
        ModelConfig second = service.create(
                request("乙", ModelDeploymentType.CLOUD, "https://b.example.com/v1", "b", "key-b"), manager);

        service.setDefault(first.id(), manager);
        service.setDefault(second.id(), manager);

        assertThat(first.defaultModel()).isFalse();
        assertThat(second.defaultModel()).isTrue();
        assertThat(repository.flushCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void refusesPhysicalDeleteWhenTheModelIsStillReferenced() {
        ModelConfig created = service.create(
                request("被引用", ModelDeploymentType.CLOUD, "https://api.example.com/v1", "glm-4", "key"), manager);
        repository.references = 3;

        assertThatThrownBy(() -> service.delete(created.id(), manager))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("只能停用");
    }

    @Test
    void listsEnabledModelsForPlainChatUsersWithoutAdministrativePermission() {
        AgentUserContext chatUser = new AgentUserContext("tenant-1", "user-9", "identity-9", Set.of(), Set.of());
        ModelConfig created = service.create(
                request("普通用户可见", ModelDeploymentType.CLOUD, "https://api.example.com/v1", "glm-4", "key"),
                manager);

        List<ModelConfig> enabled = service.enabledModels();
        assertThat(enabled).extracting(ModelConfig::id).containsExactly(created.id());

        assertThatThrownBy(() -> service.page(new ModelConfigQuery(null, null, null, null, 0, 20), chatUser))
                .isInstanceOf(ModelConfigForbiddenException.class)
                .hasMessageContaining(ModelConfigService.PERMISSION_MENU);
        assertThatThrownBy(() -> service.create(
                request("越权新增", ModelDeploymentType.CLOUD, "https://x.example.com/v1", "x", "key"), chatUser))
                .isInstanceOf(ModelConfigForbiddenException.class)
                .hasMessageContaining(ModelConfigService.PERMISSION_ADD);
    }

    @Test
    void requiresDedicatedPermissionToTestAModel() {
        ModelConfig created = service.create(
                request("待测试", ModelDeploymentType.CLOUD, "https://api.example.com/v1", "glm-4", "key"), manager);
        AgentUserContext withoutTest = new AgentUserContext("tenant-1", "user-2", "identity-2",
                Set.of(ModelConfigService.PERMISSION_MENU), Set.of());

        assertThatThrownBy(() -> service.testTarget(created.id(), withoutTest))
                .isInstanceOf(ModelConfigForbiddenException.class)
                .hasMessageContaining(ModelConfigService.PERMISSION_TEST);
    }

    @Test
    void returnsPagedResultAndMasksApiKeyWithoutRevealingIt() {
        ModelConfig created = service.create(
                request("掩码", ModelDeploymentType.CLOUD, "https://api.example.com/v1", "glm-4", "sk-abcdefghijklmn"),
                manager);

        PageResult<ModelConfig> page = service.page(new ModelConfigQuery(null, null, null, null, 0, 20), manager);
        assertThat(page.items()).hasSize(1);
        assertThat(page.total()).isEqualTo(1);

        String mask = service.apiKeyMask(created);
        assertThat(mask).isEqualTo("sk-****klmn").doesNotContain("abcdefghijklmn");
    }

    @Test
    void fullyMasksLegacyEncryptedApiKeyInPlainTextMode() {
        ModelConfigService plainTextService = new ModelConfigService(repository, new PlainTextModelSecretCipher());
        ModelConfig legacy = ModelConfig.create("历史模型", ModelProviderType.OPENAI_COMPATIBLE,
                ModelDeploymentType.CLOUD, "https://api.example.com/v1", "legacy", "v1:encrypted-payload",
                Set.of(ModelCapability.STREAMING), java.time.Duration.ofSeconds(5),
                java.time.Duration.ofSeconds(30), 1, null);

        assertThat(plainTextService.apiKeyMask(legacy)).isEqualTo("****（请重新录入）");
    }

    @Test
    void clampsPagingBoundsAndFindsNothingForUnknownId() {
        ModelConfigQuery query = new ModelConfigQuery("  ", null, null, null, -5, 1000);

        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(ModelConfigQuery.MAX_SIZE);
        assertThat(query.offset()).isZero();
        assertThat(query.name()).isNull();

        assertThatThrownBy(() -> service.detail("missing", manager))
                .isInstanceOf(ModelConfigNotFoundException.class);
    }

    private static ModelConfigRequest request(String name, ModelDeploymentType deploymentType, String baseUrl,
            String modelName, String apiKey) {
        return new ModelConfigRequest(name, deploymentType, baseUrl, modelName, apiKey,
                Set.of(ModelCapability.STREAMING), 10, 60, 1, "测试配置");
    }

    /** 仅覆盖服务层规则的内存仓储，不模拟 JPA 分页 SQL。 */
    private static final class InMemoryModelConfigRepository implements ModelConfigRepository {
        private final Map<String, ModelConfig> store = new LinkedHashMap<>();
        private long references;
        private int flushCount;

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
            return references;
        }

        @Override public void flush() {
            flushCount++;
        }
    }
}
