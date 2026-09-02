package com.smart.agent.knowledge;

import java.util.List;

public interface EmbeddingGateway {
    List<Float> embed(String text);

    String modelKey();
}
