package com.smart.agent.model.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * YAML 引导导入配置。
 *
 * <p>引导只在数据库尚无任何有效模型配置时执行一次；{@code enabled=false} 时完全跳过，
 * 运行期一律只读取数据库配置，YAML 仅作为首次引导来源。</p>
 *
 * @param enabled      是否允许首次引导导入，缺省开启
 * @param capabilities 导入模型声明的能力，逗号分隔；缺省只声明工具调用与流式输出
 */
@ConfigurationProperties("agent.model.bootstrap")
public record ModelBootstrapProperties(Boolean enabled, String capabilities) {

    /** 缺省能力：聊天链路必须的工具调用与流式输出，其余能力交由管理员在页面确认。 */
    public static final String DEFAULT_CAPABILITIES = "TOOL_CALLING,STREAMING";

    public ModelBootstrapProperties {
        enabled = enabled == null || enabled;
        capabilities = capabilities == null || capabilities.isBlank()
                ? DEFAULT_CAPABILITIES : capabilities.trim();
    }
}
