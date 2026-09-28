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
        @Valid PageContext pageContext,
        @Valid ClarificationSelection clarification) {

    public ChatCommand(String conversationId, String question, PageContext pageContext) {
        this(conversationId, question, java.util.List.of(), pageContext, null);
    }

    public ChatCommand(String conversationId, String question, java.util.List<java.util.UUID> attachmentIds,
            PageContext pageContext) {
        this(conversationId, question, attachmentIds, pageContext, null);
    }

    public String question() { return content; }

    public ChatCommand {
        if (attachmentIds == null) attachmentIds = java.util.List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record PageContext(String pageCode, String projectId, String businessType, String businessId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ClarificationSelection(
            @NotBlank @Size(max = 36) String clarificationId,
            @NotBlank @Size(max = 128) String optionId) {
    }
}
