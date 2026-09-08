package com.smart.agent.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ModelRequest(
        String runId,
        String systemInstructionVersion,
        List<ConversationEntry> redactedConversationMessages,
        List<AllowedToolSpecification> allowedToolSpecifications,
        List<RetrievedEvidence> retrievedEvidence) {

    public ModelRequest {
        requireText(runId, "runId");
        requireText(systemInstructionVersion, "systemInstructionVersion");
        if (redactedConversationMessages == null || redactedConversationMessages.isEmpty()) {
            throw new IllegalArgumentException("conversation must contain at least one message");
        }
        redactedConversationMessages = List.copyOf(redactedConversationMessages);
        allowedToolSpecifications = List.copyOf(allowedToolSpecifications);
        retrievedEvidence = List.copyOf(retrievedEvidence);
    }

    public static ModelRequest userQuestion(String runId, String question, List<String> toolKeys) {
        requireText(question, "question");
        return new ModelRequest(
                runId,
                "v1",
                List.of(new ConversationMessage("user", question)),
                toolKeys.stream().map(AllowedToolSpecification::forKey).toList(),
                List.of());
    }

    public sealed interface ConversationEntry permits ConversationMessage, ToolResultMessage {
        String content();
    }

    public record ConversationMessage(String role, String content, List<AttachmentPart> attachments) implements ConversationEntry {
        private static final Set<String> ALLOWED_ROLES = Set.of("user", "assistant");

        public ConversationMessage {
            requireText(role, "role");
            requireText(content, "content");
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
            if (!ALLOWED_ROLES.contains(role)) {
                throw new IllegalArgumentException("role must be user or assistant");
            }
        }

        public ConversationMessage(String role, String content) { this(role, content, List.of()); }
    }

    public record AttachmentPart(String url, String mediaType, String filename) {
        public AttachmentPart { requireText(url, "url"); }
    }

    public record ToolResultMessage(String callId, String toolKey, String content) implements ConversationEntry {
        public ToolResultMessage {
            requireText(callId, "callId");
            requireText(toolKey, "toolKey");
            requireText(content, "content");
        }
    }

    public record AllowedToolSpecification(String key, String description, String argumentsSchemaJson) {
        public AllowedToolSpecification {
            requireText(key, "key");
            requireText(description, "description");
            requireText(argumentsSchemaJson, "argumentsSchemaJson");
        }

        static AllowedToolSpecification forKey(String key) {
            return new AllowedToolSpecification(key, key, "{\"type\":\"object\"}");
        }
    }

    public record RetrievedEvidence(String sourceId, String content) {
        public RetrievedEvidence {
            requireText(sourceId, "sourceId");
            requireText(content, "content");
        }
    }

    private static void requireText(String value, String field) {
        if (Objects.requireNonNull(value, field).isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
