package com.smart.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;

public class LocalDeterministicModelGateway implements ModelGateway {
    private static final String PROJECT_OVERVIEW_QUESTION = "查询项目 project-1 概况";
    private static final ObjectMapper JSON = new ObjectMapper();
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
        ModelRequest.ConversationEntry last = request.redactedConversationMessages().getLast();
        if (last instanceof ModelRequest.ToolResultMessage result) {
            String answer = projectOverviewAnswer(result);
            return Flux.just(new ModelEvent.TextDelta(answer), new ModelEvent.Completed(
                    answer, tokenCount(result.content()), tokenCount(answer)));
        }
        String question = last.content();
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

    private String projectOverviewAnswer(ModelRequest.ToolResultMessage result) {
        if (!result.toolKey().equals("project.getOverview")) {
            return "本地模型: 工具调用已完成。";
        }
        try {
            JsonNode value = JSON.readTree(result.content());
            String projectName = value.path("projectName").asText();
            String status = value.path("status").asText();
            if (projectName.isBlank() || status.isBlank() || !value.path("progress").isNumber()) {
                return "本地模型: 项目概况已获取。";
            }
            long percentage = Math.round(value.path("progress").asDouble() * 100);
            return "本地模型: " + projectName + " 当前状态为 " + status + "，完成进度为 " + percentage + "% 。"
                    .replace("% 。", "%。");
        } catch (Exception ignored) {
            return "本地模型: 项目概况已获取。";
        }
    }

    private int tokenCount(String text) {
        return Math.max(1, text.codePointCount(0, text.length()) / 4);
    }
}
