package com.smart.agent.model.config;

import com.smart.agent.model.ModelGatewayProperties;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 首次启动时把 {@code agent.model} 的 YAML 配置导入数据库。
 *
 * <p>只在数据库完全没有有效模型配置时写入一条，已有记录一律不覆盖，重复启动不会产生重复数据。
 * 导入完成后运行期只读取数据库配置，YAML 仅作为首次引导来源。导入失败只记录告警，不阻断启动，
 * 下次启动仍会重试。</p>
 */
@Component
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ModelConfigBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModelConfigBootstrap.class);

    private static final String BOOTSTRAP_NAME_SUFFIX = "（YAML 引导）";
    private static final String BOOTSTRAP_REMARKS = "由 agent.model 配置首次导入；请在模型管理页核对能力声明与密钥。";

    private final ModelConfigService modelConfigService;
    private final ModelGatewayProperties yamlModel;
    private final ModelBootstrapProperties bootstrap;

    public ModelConfigBootstrap(ModelConfigService modelConfigService,
            ModelGatewayProperties yamlModel,
            ModelBootstrapProperties bootstrap) {
        this.modelConfigService = modelConfigService;
        this.yamlModel = yamlModel;
        this.bootstrap = bootstrap;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!bootstrap.enabled()) {
            log.info("模型 YAML 引导导入已关闭，运行期只使用数据库配置");
            return;
        }
        try {
            modelConfigService.bootstrapIfEmpty(bootstrapRequest())
                    .ifPresent(config -> log.info("已由 YAML 引导导入默认模型配置：{}", config.name()));
        } catch (RuntimeException failure) {
            log.warn("YAML 引导导入模型配置失败，本次启动沿用数据库现有配置：{}", failure.getMessage());
        }
    }

    private ModelConfigRequest bootstrapRequest() {
        return new ModelConfigRequest(
                yamlModel.chatModel() + BOOTSTRAP_NAME_SUFFIX,
                deploymentTypeOf(yamlModel.baseUrl()),
                yamlModel.baseUrl(),
                yamlModel.chatModel(),
                yamlModel.apiKey(),
                declaredCapabilities(),
                wholeSeconds(yamlModel.connectTimeout()),
                wholeSeconds(yamlModel.readTimeout()),
                0,
                BOOTSTRAP_REMARKS);
    }

    /**
     * YAML 无法表达部署方式，按服务地址协议推断：https 视为云端部署，其余（含 http 本地地址）视为本地部署。
     * 云端模型还必须配置 API Key，https 地址通常伴随密钥，此推断可让引导数据天然满足校验。
     */
    private static ModelDeploymentType deploymentTypeOf(String baseUrl) {
        return baseUrl != null && baseUrl.trim().toLowerCase(Locale.ROOT).startsWith("https://")
                ? ModelDeploymentType.CLOUD : ModelDeploymentType.LOCAL;
    }

    /** 配置中心只接受严格的整秒超时，亚秒配置向上取整为 1 秒。 */
    private static Integer wholeSeconds(Duration duration) {
        if (duration == null) {
            return null;
        }
        long seconds = duration.toSeconds();
        if (seconds < 1) {
            return 1;
        }
        return (int) Math.min(seconds, Integer.MAX_VALUE);
    }

    /** 解析 YAML 声明的能力，无法识别的项忽略并记录告警，不阻断导入。 */
    private Set<ModelCapability> declaredCapabilities() {
        Set<ModelCapability> parsed = new LinkedHashSet<>();
        for (String token : bootstrap.capabilities().split(",")) {
            String value = token.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                parsed.add(ModelCapability.valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException unknown) {
                log.warn("忽略无法识别的模型能力声明：{}", value);
            }
        }
        return parsed;
    }
}
