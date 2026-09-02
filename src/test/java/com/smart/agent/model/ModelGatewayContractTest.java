package com.smart.agent.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ModelGatewayContractTest {

    private final ModelRequest request = ModelRequest.userQuestion(
            "run-1", "查询项目 project-1 概况", List.of("project.getOverview"));

    @Test
    void localGatewayEmitsToolRequestThenFinalText() {
        ModelGateway gateway = new LocalDeterministicModelGateway();
        List<ModelEvent> events = eventsOf(gateway, request);

        assertThat(events)
                .hasSize(2)
                .first()
                .matches(e -> e instanceof ModelEvent.ToolRequested t
                        && t.toolKey().equals("project.getOverview")
                        && t.argumentsJson().equals("{\"projectId\":\"project-1\"}"));
        assertThat(events.get(1)).isInstanceOf(ModelEvent.Completed.class);
    }

    @Test
    void openAiGatewayStreamsTextDeltasAndTokenMetadata() {
        ModelGateway gateway = new OpenAiCompatibleModelGateway(streamingModel(handler -> {
            handler.onPartialResponse("项目");
            handler.onPartialResponse("概况已就绪");
            handler.onCompleteResponse(response("项目概况已就绪", 17, 5));
        }), Duration.ofSeconds(1));

        assertThat(eventsOf(gateway, request)).containsExactly(
                new ModelEvent.TextDelta("项目"),
                new ModelEvent.TextDelta("概况已就绪"),
                new ModelEvent.Completed("项目概况已就绪", 17, 5));
    }

    @Test
    void openAiGatewayMapsProviderTimeoutToStableFailedEvent() {
        ModelGateway gateway = new OpenAiCompatibleModelGateway(streamingModel(handler -> {
            // Intentionally never calls back, simulating a provider read timeout.
        }), Duration.ofMillis(20));

        assertThat(eventsOf(gateway, request)).containsExactly(
                new ModelEvent.Failed("MODEL_PROVIDER_TIMEOUT", "Model provider timed out"));
    }

    @Test
    void openAiGatewayMapsMalformedToolArgumentsWithoutLeakingProviderBody() {
        ModelGateway gateway = new OpenAiCompatibleModelGateway(streamingModel(handler -> {
            handler.onCompleteToolCall(new CompleteToolCall(0, ToolExecutionRequest.builder()
                    .id("call-1")
                    .name("project.getOverview")
                    .arguments("not-json-secret-body")
                    .build()));
        }), Duration.ofSeconds(1));

        assertThat(eventsOf(gateway, request)).containsExactly(
                new ModelEvent.Failed("MODEL_TOOL_ARGUMENTS_INVALID", "Model requested invalid tool arguments"));
    }

    @Test
    void openAiGatewayMapsProviderErrorWithoutLeakingBodyOrSecret() {
        ModelGateway gateway = new OpenAiCompatibleModelGateway(streamingModel(handler ->
                handler.onError(new IllegalStateException("Bearer private-key request-body"))), Duration.ofSeconds(1));

        assertThat(eventsOf(gateway, request)).singleElement()
                .matches(e -> e instanceof ModelEvent.Failed failed
                        && failed.code().equals("MODEL_PROVIDER_ERROR")
                        && !failed.message().contains("private-key")
                        && !failed.message().contains("request-body"));
    }

    @Test
    void configurationSelectsExactlyOneGatewayForEachMode() {
        new ApplicationContextRunner()
                .withUserConfiguration(ModelGatewayConfiguration.class)
                .withPropertyValues("agent.model.mode=local")
                .run(context -> assertThat(context).hasSingleBean(LocalDeterministicModelGateway.class)
                        .doesNotHaveBean(OpenAiCompatibleModelGateway.class));

        new ApplicationContextRunner()
                .withUserConfiguration(ModelGatewayConfiguration.class)
                .withPropertyValues(
                        "agent.model.mode=openai-compatible",
                        "agent.model.base-url=http://localhost:9999/v1",
                        "agent.model.api-key=test-key",
                        "agent.model.chat-model=test-model")
                .run(context -> assertThat(context).hasSingleBean(OpenAiCompatibleModelGateway.class)
                        .doesNotHaveBean(LocalDeterministicModelGateway.class));
    }

    private static StreamingChatModel streamingModel(Consumer<StreamingChatResponseHandler> action) {
        return new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                action.accept(handler);
            }
        };
    }

    private static ChatResponse response(String text, int inputTokens, int outputTokens) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.builder().text(text).build())
                .tokenUsage(new TokenUsage(inputTokens, outputTokens))
                .build();
    }

    private static List<ModelEvent> eventsOf(ModelGateway gateway, ModelRequest request) {
        return gateway.stream(request).collectList().block(Duration.ofSeconds(2));
    }
}
