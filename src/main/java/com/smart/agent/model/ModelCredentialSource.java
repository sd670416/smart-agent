package com.smart.agent.model;

/**
 * 按模型 ID 提供运行时凭据。
 *
 * <p>API Key 不随工具上下文流转，需要发请求的能力（如联网搜索）通过本接口按需换取，
 * 换取结果仅用于当次请求，禁止写入日志或对外返回。
 *
 * <p><b>运行期不要再调用 {@link #credentialsFor(String)}</b>：它按模型 ID 读取数据库里
 * <em>最新</em>的配置，一轮对话中途改配置会让聊天与工具使用不同版本。
 * 一次运行内的凭据应取自执行句柄并经 {@link ModelRunContext} 传递给工具；
 * 本方法只服务于没有运行上下文的兜底路径（未接入模型配置中心的部署、直接调用等）。
 */
@FunctionalInterface
public interface ModelCredentialSource {

    /**
     * 取得指定模型<em>当前最新</em>的连接信息。
     *
     * @param modelId 模型配置 ID
     * @return 连接信息；模型不存在或已删除时返回 {@code null}
     */
    Credentials credentialsFor(String modelId);

    /**
     * 模型连接信息，仅在使用瞬间存在于内存中。
     *
     * <p>{@code toString()} 已做掩码：该记录可能被异常链或调试语句打印，
     * 绝不能把 API Key 带进任何输出。
     */
    record Credentials(String baseUrl, String apiKey, String modelName) {
        @Override
        public String toString() {
            return "Credentials[baseUrl=" + baseUrl + ", hasApiKey=" + hasApiKey() + ", modelName=" + modelName + "]";
        }

        /** 是否携带可用的 API Key，用于排障时判断凭据是否配齐（不暴露内容）。 */
        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    /** 未接入模型配置中心时的空实现。 */
    static ModelCredentialSource empty() {
        return modelId -> null;
    }
}
