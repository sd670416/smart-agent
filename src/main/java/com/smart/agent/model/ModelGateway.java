package com.smart.agent.model;

import reactor.core.publisher.Flux;

public interface ModelGateway {
    Flux<ModelEvent> stream(ModelRequest request);
}
