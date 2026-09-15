package com.smart.agent.model.config;

import com.smart.agent.knowledge.manage.PageResult;
import com.smart.agent.security.AgentUserContext;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模型配置管理服务。
 *
 * <p>模型配置为全系统统一维护的全局配置，不按租户隔离。管理操作全部要求对应的菜单或按钮权限；
 * API Key 为简化部署直接落库，但接口响应一律脱敏，服务本身不记录明文密钥。</p>
 */
@Service
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ModelConfigService {

    /** 模型管理菜单权限：控制管理页读取。 */
    public static final String PERMISSION_MENU = "aiModel:menu";
    public static final String PERMISSION_ADD = "aiModel:add";
    public static final String PERMISSION_UPDATE = "aiModel:update";
    public static final String PERMISSION_DELETE = "aiModel:delete";
    public static final String PERMISSION_ENABLE = "aiModel:enable";
    public static final String PERMISSION_SET_DEFAULT = "aiModel:setDefault";
    public static final String PERMISSION_TEST = "aiModel:test";

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);

    /** 引导导入记录的操作人标识，与真实登录用户区分。 */
    private static final String BOOTSTRAP_OPERATOR = "system:model-config-bootstrap";

    private final ModelConfigRepository repository;
    private final ModelSecretCipher cipher;

    @Autowired
    public ModelConfigService(ModelConfigRepository repository, ModelSecretCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    @Transactional(readOnly = true)
    public PageResult<ModelConfig> page(ModelConfigQuery query, AgentUserContext actor) {
        requirePermission(actor, PERMISSION_MENU);
        long total = repository.count(query);
        return new PageResult<>(repository.findPage(query), total, query.page(), query.size());
    }

    @Transactional(readOnly = true)
    public ModelConfig detail(String id, AgentUserContext actor) {
        requirePermission(actor, PERMISSION_MENU);
        return existing(id);
    }

    @Transactional(readOnly = true)
    public ModelConfig testTarget(String id, AgentUserContext actor) {
        requirePermission(actor, PERMISSION_TEST);
        return existing(id);
    }

    /**
     * 已启用模型列表。
     *
     * <p>供聊天页切换模型使用：只要持有可信登录上下文即可读取，不额外要求管理按钮权限，
     * 返回内容由调用方裁剪为不含密钥的摘要。</p>
     */
    @Transactional(readOnly = true)
    public List<ModelConfig> enabledModels() {
        return repository.findEnabled();
    }

    /**
     * 首次启动引导导入：仅在数据库尚无任何有效模型配置时写入一条，并设为系统默认。
     *
     * <p>已有记录一律不覆盖，保证重复启动幂等。导入内容仍走与页面新增完全相同的地址与密钥校验，
     * 只是不参与按钮权限判断——引导发生在还没有任何登录用户的启动阶段。</p>
     *
     * @return 实际写入的配置；数据库已有记录时返回空
     */
    @Transactional
    public Optional<ModelConfig> bootstrapIfEmpty(ModelConfigRequest request) {
        if (!repository.findAllActive().isEmpty()) {
            return Optional.empty();
        }
        requireRequest(request);
        ModelDeploymentType deploymentType = requiredDeployment(request.deploymentType());
        ModelConfig config = ModelConfig.create(required(request.name(), "模型展示名称"),
                ModelProviderType.OPENAI_COMPATIBLE,
                deploymentType,
                endpoint(request.baseUrl(), deploymentType),
                required(request.modelName(), "实际请求模型名称"),
                encryptedKey(request.apiKey(), deploymentType),
                capabilities(request.capabilities()),
                seconds(request.connectTimeoutSeconds(), DEFAULT_CONNECT_TIMEOUT),
                seconds(request.readTimeoutSeconds(), DEFAULT_READ_TIMEOUT),
                request.sort() == null ? 0 : request.sort(),
                trimToNull(request.remarks()));
        config.makeDefault();
        config.markCreatedBy(BOOTSTRAP_OPERATOR);
        return Optional.of(repository.save(config));
    }

    @Transactional
    public ModelConfig create(ModelConfigRequest request, AgentUserContext actor) {
        requirePermission(actor, PERMISSION_ADD);
        requireRequest(request);
        ModelDeploymentType deploymentType = requiredDeployment(request.deploymentType());
        ModelConfig config = ModelConfig.create(required(request.name(), "模型展示名称"),
                ModelProviderType.OPENAI_COMPATIBLE,
                deploymentType,
                endpoint(request.baseUrl(), deploymentType),
                required(request.modelName(), "实际请求模型名称"),
                encryptedKey(request.apiKey(), deploymentType),
                capabilities(request.capabilities()),
                seconds(request.connectTimeoutSeconds(), DEFAULT_CONNECT_TIMEOUT),
                seconds(request.readTimeoutSeconds(), DEFAULT_READ_TIMEOUT),
                request.sort() == null ? 0 : request.sort(),
                trimToNull(request.remarks()));
        config.markCreatedBy(actor.userId());
        return repository.save(config);
    }

    @Transactional
    public ModelConfig update(String id, ModelConfigRequest request, AgentUserContext actor) {
        requirePermission(actor, PERMISSION_UPDATE);
        requireRequest(request);
        ModelConfig current = existing(id);
        ModelDeploymentType deploymentType = requiredDeployment(request.deploymentType());
        current.update(required(request.name(), "模型展示名称"), deploymentType,
                endpoint(request.baseUrl(), deploymentType),
                required(request.modelName(), "实际请求模型名称"),
                encryptedKeyForUpdate(request.apiKey(), deploymentType, current),
                capabilities(request.capabilities()),
                seconds(request.connectTimeoutSeconds(), DEFAULT_CONNECT_TIMEOUT),
                seconds(request.readTimeoutSeconds(), DEFAULT_READ_TIMEOUT),
                request.sort() == null ? 0 : request.sort(),
                trimToNull(request.remarks()));
        current.markUpdatedBy(actor.userId());
        return repository.save(current);
    }

    @Transactional
    public void delete(String id, AgentUserContext actor) {
        requirePermission(actor, PERMISSION_DELETE);
        ModelConfig current = existing(id);
        if (repository.countReferences(id) > 0) {
            throw new IllegalStateException("该模型已被会话或运行记录引用，只能停用，不能删除");
        }
        // 默认模型不允许删除，由领域模型保证
        current.delete();
        current.markUpdatedBy(actor.userId());
        repository.save(current);
    }

    @Transactional
    public ModelConfig setEnabled(String id, boolean enabled, AgentUserContext actor) {
        requirePermission(actor, PERMISSION_ENABLE);
        ModelConfig current = existing(id);
        // 默认模型不允许停用，由领域模型保证
        current.setEnabled(enabled);
        current.markUpdatedBy(actor.userId());
        return repository.save(current);
    }

    /**
     * 切换系统默认模型。
     *
     * <p>先清除旧默认并 flush，让数据库唯一默认槽位先行释放，再设置新默认，
     * 避免同一事务内两条记录短暂同时持有默认标记而触发唯一约束冲突。</p>
     */
    @Transactional
    public ModelConfig setDefault(String id, AgentUserContext actor) {
        requirePermission(actor, PERMISSION_SET_DEFAULT);
        ModelConfig target = existing(id);
        if (!target.enabled()) {
            throw new IllegalStateException("默认模型必须处于启用状态，请先启用该模型");
        }
        repository.findDefault()
                .filter(current -> !current.id().equals(id))
                .ifPresent(current -> {
                    current.clearDefault();
                    repository.save(current);
                    repository.flush();
                });
        target.makeDefault();
        target.markUpdatedBy(actor.userId());
        return repository.save(target);
    }

    /**
     * 生成 API Key 掩码，仅供管理端详情响应使用，不写入日志。
     *
     * @return 未配置密钥时返回 {@code null}
     */
    @Transactional(readOnly = true)
    public String apiKeyMask(ModelConfig config) {
        if (cipher instanceof PlainTextModelSecretCipher plainTextCipher
                && plainTextCipher.isLegacyEncryptedValue(config.encryptedApiKey())) {
            return "****（请重新录入）";
        }
        String plain = cipher.decrypt(config.encryptedApiKey());
        if (plain == null) {
            return null;
        }
        if (plain.length() <= 8) {
            return "****";
        }
        return plain.substring(0, 3) + "****" + plain.substring(plain.length() - 4);
    }

    private ModelConfig existing(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("模型配置 ID 不能为空");
        }
        return repository.findById(id).orElseThrow(() -> new ModelConfigNotFoundException(id));
    }

    /** 新增：云端模型必须配置 API Key，本地模型允许为空。 */
    private String encryptedKey(String apiKey, ModelDeploymentType deploymentType) {
        String encrypted = cipher.encrypt(apiKey);
        if (encrypted == null && deploymentType == ModelDeploymentType.CLOUD) {
            throw new IllegalArgumentException("云端模型必须配置 API Key");
        }
        return encrypted;
    }

    /** 修改：留空表示保留原密钥，不会被清空。 */
    private String encryptedKeyForUpdate(String apiKey, ModelDeploymentType deploymentType, ModelConfig current) {
        String encrypted = cipher.encrypt(apiKey);
        if (encrypted != null) {
            return encrypted;
        }
        if (current.encryptedApiKey() == null && deploymentType == ModelDeploymentType.CLOUD) {
            throw new IllegalArgumentException("云端模型必须配置 API Key");
        }
        return null;
    }

    private static void requirePermission(AgentUserContext actor, String permission) {
        if (actor == null || !actor.permissions().contains(permission)) {
            throw new ModelConfigForbiddenException(permission);
        }
    }

    private static void requireRequest(ModelConfigRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求内容不能为空");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return value.trim();
    }

    private static ModelDeploymentType requiredDeployment(ModelDeploymentType value) {
        if (value == null) {
            throw new IllegalArgumentException("部署方式不能为空，可选 CLOUD 或 LOCAL");
        }
        return value;
    }

    private static Set<ModelCapability> capabilities(Set<ModelCapability> values) {
        return values == null ? Set.of() : values;
    }

    private static Duration seconds(Integer value, Duration fallback) {
        return value == null ? fallback : Duration.ofSeconds(value);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 校验服务地址：云端必须 https，本地允许 http/https，
     * 并拒绝指向云元数据地址、链路本地地址和泛地址的目标，收敛 SSRF 面。
     */
    private static String endpoint(String baseUrl, ModelDeploymentType deploymentType) {
        String value = required(baseUrl, "模型服务地址");
        URI uri;
        try {
            uri = new URI(value);
        } catch (Exception failure) {
            throw new IllegalArgumentException("模型服务地址格式不正确");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("模型服务地址只支持 http 或 https 协议");
        }
        if (deploymentType == ModelDeploymentType.CLOUD && !"https".equals(scheme)) {
            throw new IllegalArgumentException("云端模型必须使用 https 服务地址");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("模型服务地址缺少主机名");
        }
        if (blockedHost(host)) {
            throw new IllegalArgumentException("模型服务地址不允许指向云元数据、链路本地或泛监听地址");
        }
        return value;
    }

    private static boolean blockedHost(String host) {
        String value = host.toLowerCase(Locale.ROOT);
        if (value.equals("metadata") || value.equals("metadata.google.internal")) {
            return true;
        }
        if (!isIpLiteral(value)) {
            // 域名不在保存阶段解析，避免保存被 DNS 阻塞；调用期仍受超时和重定向限制保护
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            return address.isLinkLocalAddress() || address.isAnyLocalAddress();
        } catch (UnknownHostException failure) {
            return false;
        }
    }

    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.chars().allMatch(character -> Character.isDigit(character) || character == '.');
    }
}
