package com.smart.agent.chat;

import com.smart.agent.security.AgentUserContext;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/agent/chat")
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChatController {
    private final ChatSseUseCase chat;

    public ChatController(ChatSseUseCase chat) {
        this.chat = chat;
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @Valid @RequestBody ChatCommand command,
            @RequestAttribute("com.smart.agent.security.AgentUserContext") AgentUserContext context,
            @RequestAttribute("com.smart.agent.common.trace.TraceIdFilter.traceId") String traceId) {
        return chat.stream(command, context, traceId);
    }
}
