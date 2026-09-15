package com.smart.agent.model.config;

/**
 * 模型密钥加解密失败。
 *
 * <p>异常信息只描述失败原因，不包含明文密钥和完整密文。</p>
 */
public class ModelSecretException extends RuntimeException {

    public ModelSecretException(String message) {
        super(message);
    }

    public ModelSecretException(String message, Throwable cause) {
        super(message, cause);
    }
}
