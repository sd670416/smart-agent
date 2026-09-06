package com.smart.agent.attachment;

public final class AttachmentNotFoundException extends IllegalArgumentException {
    public AttachmentNotFoundException() {
        super("Attachment not found");
    }
}
