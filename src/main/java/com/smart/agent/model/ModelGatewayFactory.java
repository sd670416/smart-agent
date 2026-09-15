package com.smart.agent.model;

import com.smart.agent.model.config.ModelConfig;

/** 根据持久化配置创建模型网关。 */
public interface ModelGatewayFactory {

    CreatedGateway create(ModelConfig config, String apiKey);

    record CreatedGateway(ModelGateway gateway, Runnable closeAction) {
        public CreatedGateway {
            if (gateway == null) throw new IllegalArgumentException("gateway must not be null");
            closeAction = closeAction == null ? () -> { } : closeAction;
        }
    }
}
