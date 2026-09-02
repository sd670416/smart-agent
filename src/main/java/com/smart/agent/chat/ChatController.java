package com.smart.agent.chat;

import com.smart.agent.common.trace.TraceIdFilter;
import com.smart.agent.security.AgentUserContext;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/agent/chat")
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChatController {
    private final ChatOrchestrator orchestrator;

    public ChatController(ChatOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatEvent>> stream(
            @Valid @RequestBody ChatCommand command,
            @RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext context,
            @RequestAttribute("com.smart.agent.common.trace.TraceIdFilter.traceId") String traceId) {
        return orchestrator.stream(command, context, traceId)
                .map(event -> ServerSentEvent.<ChatEvent>builder(event).event(event.type()).build());
    }
}
