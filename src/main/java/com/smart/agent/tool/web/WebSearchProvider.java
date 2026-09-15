package com.smart.agent.tool.web;

import com.smart.agent.tool.ToolContext;

/**
 * 联网搜索提供方。
 *
 * <p>抽象方法保持单参数形态，便于测试用 lambda 打桩。需要感知本轮模型绑定的实现
 * （如两个真实提供方）覆写 {@link #search(WebSearchInput, ToolContext)}。
 */
@FunctionalInterface
public interface WebSearchProvider {
    /**
     * 执行联网搜索。
     *
     * @param input 归一化后的搜索条件
     */
    WebSearchResult search(WebSearchInput input);

    /**
     * 携带本轮模型绑定执行联网搜索。
     *
     * <p>默认实现忽略上下文并回退到 {@link #search(WebSearchInput)}，
     * 保证不关心模型的提供方无需改动。
     *
     * @param context 工具上下文，携带本轮会话所选模型；可能为 {@code null}
     */
    default WebSearchResult search(WebSearchInput input, ToolContext context) {
        return search(input);
    }
}
