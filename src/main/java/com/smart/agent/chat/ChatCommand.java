package com.smart.agent.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record ChatCommand(
        @NotBlank String conversationId,
        @NotBlank @Size(max = 4000) String content,
        @Size(max = 10) java.util.List<java.util.UUID> attachmentIds,
        @Valid PageContext pageContext) {

    public ChatCommand(String conversationId, String question, PageContext pageContext) {
        this(conversationId, question, java.util.List.of(), pageContext);
    }

    public String question() { return content; }

    public ChatCommand {
        if (attachmentIds == null) attachmentIds = java.util.List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record PageContext(String pageCode, String projectId, String businessType, String businessId) {
    }
}
