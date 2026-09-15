package com.smart.agent.model.config;

/**
 * 无主密钥的 API Key 存储实现。
 *
 * <p>API Key 按原文持久化以简化部署，但接口和日志仍必须保持脱敏。该实现无法解密历史
 * AES-GCM 数据，遇到 {@code v1:} 前缀时要求管理员重新录入密钥。</p>
 */
public final class PlainTextModelSecretCipher implements ModelSecretCipher {

    @Override
    public String encrypt(String plainText) {
        return normalize(plainText);
    }

    @Override
    public String decrypt(String storedValue) {
        String value = normalize(storedValue);
        if (isLegacyEncryptedValue(value)) {
            throw new ModelSecretException("检测到历史加密的 API Key，当前部署不使用主密钥，请重新录入该模型的 API Key");
        }
        return value;
    }

    public boolean isLegacyEncryptedValue(String storedValue) {
        return storedValue != null && storedValue.startsWith("v1:");
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
