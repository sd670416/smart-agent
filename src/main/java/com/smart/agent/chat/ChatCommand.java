package com.smart.agent.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record ChatCommand(
        @NotBlank String conversationId,
        @NotBlank @Size(max = 4000) String question,
        @Valid PageContext pageContext) {

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record PageContext(String pageCode, String projectId, String businessType, String businessId) {
    }
}
