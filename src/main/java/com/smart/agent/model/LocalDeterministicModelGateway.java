package com.smart.agent.model;

import reactor.core.publisher.Flux;

public class LocalDeterministicModelGateway implements ModelGateway {
    private static final String PROJECT_OVERVIEW_QUESTION = "查询项目 project-1 概况";

    @Override
    public Flux<ModelEvent> stream(ModelRequest request) {
        String question = request.redactedConversationMessages().getLast().content();
        if (PROJECT_OVERVIEW_QUESTION.equals(question) && allowsProjectOverview(request)) {
            return Flux.just(
                    new ModelEvent.ToolRequested(
                            "local-" + request.runId(), "project.getOverview", "{\"projectId\":\"project-1\"}"),
                    new ModelEvent.Completed("", 0, 0));
        }

        String answer = "本地模型: " + question;
        return Flux.just(new ModelEvent.TextDelta(answer), new ModelEvent.Completed(answer, 0, 0));
    }

    private boolean allowsProjectOverview(ModelRequest request) {
        return request.allowedToolSpecifications().stream()
                .anyMatch(tool -> tool.key().equals("project.getOverview"));
    }
}
