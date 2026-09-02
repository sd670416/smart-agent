package com.smart.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

public class OpenAiCompatibleModelGateway implements ModelGateway {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StreamingChatModel model;
    private final Duration readTimeout;

    public OpenAiCompatibleModelGateway(StreamingChatModel model, Duration readTimeout) {
        this.model = model;
        this.readTimeout = readTimeout;
    }

    @Override
    public Flux<ModelEvent> stream(ModelRequest request) {
        return Flux.<ModelEvent>create(sink -> invokeModel(request, sink))
                .timeout(readTimeout)
                .onErrorResume(TimeoutException.class,
                        error -> Flux.just(new ModelEvent.Failed("MODEL_PROVIDER_TIMEOUT", "Model provider timed out")))
                .onErrorResume(error -> Flux.just(new ModelEvent.Failed("MODEL_PROVIDER_ERROR", "Model provider request failed")));
    }

    private void invokeModel(ModelRequest request, FluxSink<ModelEvent> sink) {
        AtomicBoolean terminated = new AtomicBoolean();
        StringBuilder streamedText = new StringBuilder();
        Set<String> emittedCallIds = new HashSet<>();
        sink.onCancel(() -> terminated.set(true));
        try {
            model.chat(toChatRequest(request), new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String text) {
                    if (text != null && !text.isEmpty() && canEmit(sink, terminated)) {
                        streamedText.append(text);
                        sink.next(new ModelEvent.TextDelta(text));
                    }
                }

                @Override
                public void onCompleteToolCall(CompleteToolCall toolCall) {
                    emitToolRequest(toolCall.toolExecutionRequest(), request, sink, terminated, emittedCallIds);
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    if (terminated.get()) {
                        return;
                    }
                    for (ToolExecutionRequest tool : response.aiMessage().toolExecutionRequests()) {
                        emitToolRequest(tool, request, sink, terminated, emittedCallIds);
                    }
                    if (!terminated.compareAndSet(false, true) || sink.isCancelled()) {
                        return;
                    }
                    String finalText = response.aiMessage().text();
                    if (finalText == null || finalText.isBlank()) {
                        finalText = streamedText.toString();
                    }
                    int inputTokens = response.tokenUsage() == null || response.tokenUsage().inputTokenCount() == null
                            ? 0
                            : response.tokenUsage().inputTokenCount();
                    int outputTokens = response.tokenUsage() == null || response.tokenUsage().outputTokenCount() == null
                            ? 0
                            : response.tokenUsage().outputTokenCount();
                    sink.next(new ModelEvent.Completed(finalText, inputTokens, outputTokens));
                    sink.complete();
                }

                @Override
                public void onError(Throwable error) {
                    completeWith(sink, terminated, new ModelEvent.Failed("MODEL_PROVIDER_ERROR", "Model provider request failed"));
                }
            });
        } catch (RuntimeException error) {
            completeWith(sink, terminated, new ModelEvent.Failed("MODEL_PROVIDER_ERROR", "Model provider request failed"));
        }
    }

    private void emitToolRequest(
            ToolExecutionRequest tool,
            ModelRequest request,
            FluxSink<ModelEvent> sink,
            AtomicBoolean terminated,
            Set<String> emittedCallIds) {
        if (!canEmit(sink, terminated) || !emittedCallIds.add(tool.id())) {
            return;
        }
        if (request.allowedToolSpecifications().stream().noneMatch(specification -> specification.key().equals(tool.name()))) {
            completeWith(sink, terminated, new ModelEvent.Failed("MODEL_TOOL_NOT_ALLOWED", "Model requested an unavailable tool"));
            return;
        }
        if (!isJsonObject(tool.arguments())) {
            completeWith(sink, terminated,
                    new ModelEvent.Failed("MODEL_TOOL_ARGUMENTS_INVALID", "Model requested invalid tool arguments"));
            return;
        }
        sink.next(new ModelEvent.ToolRequested(tool.id(), tool.name(), tool.arguments()));
    }

    private static boolean canEmit(FluxSink<ModelEvent> sink, AtomicBoolean terminated) {
        return !terminated.get() && !sink.isCancelled();
    }

    private static void completeWith(FluxSink<ModelEvent> sink, AtomicBoolean terminated, ModelEvent event) {
        if (terminated.compareAndSet(false, true) && !sink.isCancelled()) {
            sink.next(event);
            sink.complete();
        }
    }

    private static boolean isJsonObject(String arguments) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(arguments);
            return node != null && node.isObject();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static ChatRequest toChatRequest(ModelRequest request) {
        return ChatRequest.builder()
                .messages(request.redactedConversationMessages().stream()
                        .map(message -> message.role().equals("system")
                                ? SystemMessage.from(message.content())
                                : UserMessage.from(message.content()))
                        .toList())
                .toolSpecifications(request.allowedToolSpecifications().stream()
                        .map(OpenAiCompatibleModelGateway::toToolSpecification)
                        .toList())
                .build();
    }

    private static ToolSpecification toToolSpecification(ModelRequest.AllowedToolSpecification specification) {
        return ToolSpecification.builder()
                .name(specification.key())
                .description(specification.description())
                .parameters(JsonObjectSchema.builder().additionalProperties(true).build())
                .build();
    }
}
