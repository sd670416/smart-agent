package com.smart.agent.model.config;

/** 模型配置不存在或已被删除。 */
public class ModelConfigNotFoundException extends RuntimeException {

    public ModelConfigNotFoundException(String id) {
        super("模型配置不存在或已被删除：" + id);
    }
}
