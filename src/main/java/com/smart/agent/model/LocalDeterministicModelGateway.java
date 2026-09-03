package com.smart.agent.model;

import reactor.core.publisher.Flux;

public class LocalDeterministicModelGateway implements ModelGateway {
    private static final String PROJECT_OVERVIEW_QUESTION = "查询项目 project-1 概况";
    private final SystemInstructionCatalog instructionCatalog;

    public LocalDeterministicModelGateway() {
        this(new SystemInstructionCatalog());
    }

    LocalDeterministicModelGateway(SystemInstructionCatalog instructionCatalog) {
        this.instructionCatalog = instructionCatalog;
    }

    @Override
    public Flux<ModelEvent> stream(ModelRequest request) {
        if (instructionCatalog.resolve(request.systemInstructionVersion()).isEmpty()) {
            return Flux.just(new ModelEvent.Failed(
                    "MODEL_INSTRUCTION_VERSION_UNSUPPORTED", "Model instruction version is not supported"));
        }
        String question = request.redactedConversationMessages().getLast().content();
        if (question.startsWith(PROJECT_OVERVIEW_QUESTION) && allowsProjectOverview(request)) {
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
