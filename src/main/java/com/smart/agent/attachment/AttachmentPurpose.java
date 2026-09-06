package com.smart.agent.attachment;

public enum AttachmentPurpose {
    CHAT_ATTACHMENT("chat-attachment"),
    KNOWLEDGE_DOCUMENT("knowledge-document");

    private final String objectKeySegment;

    AttachmentPurpose(String objectKeySegment) {
        this.objectKeySegment = objectKeySegment;
    }

    public String objectKeySegment() {
        return objectKeySegment;
    }
}
