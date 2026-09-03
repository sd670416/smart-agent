package com.smart.agent.chat;

import com.smart.agent.security.AgentUserContext;
import java.io.IOException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

public class ChatSseUseCase {
    private final ChatOrchestrator orchestrator;

    public ChatSseUseCase(ChatOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public SseEmitter stream(ChatCommand command, AgentUserContext context, String traceId) {
        SseEmitter emitter = new SseEmitter(ChatOrchestrator.MAX_RUN_DURATION.toMillis());
        Disposable subscription = orchestrator.stream(command, context, traceId).subscribe(
                event -> send(emitter, event),
                emitter::completeWithError,
                emitter::complete);
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(subscription::dispose);
        emitter.onError(ignored -> subscription.dispose());
        return emitter;
    }

    private void send(SseEmitter emitter, ChatEvent event) {
        try {
            emitter.send(SseEmitter.event().name(event.type()).data(event));
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }
}
