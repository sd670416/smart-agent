package com.smart.agent.model;

/**
 * 按模型 ID 提供运行时凭据。
 *
 * <p>API Key 不随工具上下文流转，需要发请求的能力（如联网搜索）通过本接口按需换取，
 * 换取结果仅用于当次请求，禁止写入日志或对外返回。
 */
@FunctionalInterface
public interface ModelCredentialSource {

    /**
     * 取得指定模型的连接信息。
     *
     * @param modelId 模型配置 ID
     * @return 连接信息；模型不存在或已删除时返回 {@code null}
     */
    Credentials credentialsFor(String modelId);

    /**
     * 模型连接信息，仅在使用瞬间存在于内存中。
     */
    record Credentials(String baseUrl, String apiKey, String modelName) {
    }

    /** 未接入模型配置中心时的空实现。 */
    static ModelCredentialSource empty() {
        return modelId -> null;
    }
}
