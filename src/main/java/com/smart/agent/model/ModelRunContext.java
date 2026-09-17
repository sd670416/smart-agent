package com.smart.agent.model;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 单次运行的私有模型上下文。
 *
 * <p>承载本轮运行绑定的模型凭据，供联网搜索等需要直接发起 HTTP 请求的能力读取。
 * 凭据与 {@link ModelExecutionHandle} 同源同版本，因此配置在一轮对话中途被修改时，
 * 聊天与所有工具仍然使用同一份 {@code baseUrl / modelName / apiKey}，
 * 不会出现"聊天用旧版本、联网用新版本"的错配。
 *
 * <p><b>安全约束（改动前必读）</b>：内容只存在于执行工具的工作线程内——
 * <ul>
 *   <li><b>不进入</b> {@link com.smart.agent.tool.ToolContext}；</li>
 *   <li><b>不参与</b>序列化，<b>不写入</b>日志与调试事件，<b>不返回</b>给接口调用方；</li>
 *   <li>工具执行结束（含异常）后立即清理，避免线程复用导致凭据残留。</li>
 * </ul>
 *
 * <p>之所以用线程私有而非放进工具上下文：工具上下文可能被序列化或打印，
 * 而密钥一旦进入这些路径就是泄露。
 */
public final class ModelRunContext {

    private static final ThreadLocal<ModelCredentialSource.Credentials> CURRENT = new ThreadLocal<>();

    private ModelRunContext() {
    }

    /**
     * 在指定凭据的上下文中执行动作，无论正常返回还是抛异常都会恢复原有上下文。
     *
     * @param credentials 本轮运行的模型凭据；{@code null} 表示显式清除，
     *                    此时工具按“本轮无可用凭据”处理，而不是回退查询最新配置
     */
    public static <T> T with(ModelCredentialSource.Credentials credentials, Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        ModelCredentialSource.Credentials previous = CURRENT.get();
        apply(credentials);
        try {
            return action.get();
        } finally {
            apply(previous);
        }
    }

    /**
     * 取当前运行绑定的模型凭据。
     *
     * @return 本轮凭据；不在运行上下文内（例如未接入模型配置中心的部署）时返回 {@code null}
     */
    public static ModelCredentialSource.Credentials credentials() {
        return CURRENT.get();
    }

    /** 当前线程是否处于运行上下文内，仅供测试断言清理逻辑。 */
    static boolean isActive() {
        return CURRENT.get() != null;
    }

    private static void apply(ModelCredentialSource.Credentials value) {
        if (value == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(value);
        }
    }
}
