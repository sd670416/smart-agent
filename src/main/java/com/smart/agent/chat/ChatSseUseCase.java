package com.smart.agent.chat;

import com.smart.agent.security.AgentUserContext;
import java.io.IOException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import java.time.Duration;

public class ChatSseUseCase {
    private final ChatOrchestrator orchestrator;

    public ChatSseUseCase(ChatOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public SseEmitter stream(ChatCommand command, AgentUserContext context, String traceId) {
        SseEmitter emitter = new SseEmitter(ChatOrchestrator.MAX_RUN_DURATION.toMillis());
        Disposable.Composite lifecycle = Disposables.composite();
        emitter.onCompletion(lifecycle::dispose);
        emitter.onTimeout(lifecycle::dispose);
        emitter.onError(ignored -> lifecycle.dispose());
        Flux<SseEmitter.SseEventBuilder> events = orchestrator.stream(command, context, traceId)
                .map(event -> SseEmitter.event().name(event.type()).data(event));
        // Start preparation off the servlet thread so the response can flush before model routing completes.
        Disposable subscription = events.publish(shared -> Flux.merge(shared,
                        Flux.interval(Duration.ofSeconds(15))
                                .map(tick -> SseEmitter.event().comment("keep-alive"))
                                .takeUntilOther(shared.ignoreElements())))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(event -> send(emitter, event), emitter::completeWithError, emitter::complete);
        lifecycle.add(subscription);
        return emitter;
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException exception) {
            emitter.completeWithError(exception);
        }
    }
}
