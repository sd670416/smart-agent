package com.smart.agent.model.config;

/**
 * 模型 API Key 持久化转换契约。
 *
 * <p>当前部署使用明文实现以免维护主密钥，但 API Key 不得写入日志、异常信息和接口响应。</p>
 */
public interface ModelSecretCipher {

    /**
     * 转换待存储的密钥。
     *
     * @param plainText 明文 API Key；{@code null} 或空白表示未配置密钥，直接返回 {@code null}
     * @return 持久化值；无密钥时返回 {@code null}
     * @throws ModelSecretException 存储转换失败
     */
    String encrypt(String plainText);

    /**
     * 读取已存储的密钥。
     *
     * @param cipherText 已存储的值；{@code null} 或空白表示未配置密钥，直接返回 {@code null}
     * @return 明文 API Key；无密钥时返回 {@code null}
     * @throws ModelSecretException 已存储内容无法读取
     */
    String decrypt(String cipherText);
}
