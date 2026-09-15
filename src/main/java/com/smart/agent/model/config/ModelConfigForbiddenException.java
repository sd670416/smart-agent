package com.smart.agent.model.config;

/** 当前用户缺少模型配置管理所需的菜单或按钮权限。 */
public class ModelConfigForbiddenException extends RuntimeException {

    public ModelConfigForbiddenException(String permission) {
        super("缺少模型管理权限：" + permission);
    }
}
