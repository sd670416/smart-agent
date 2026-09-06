package com.smart.agent.attachment;

import java.util.UUID;

public record CompleteUploadCommand(
        UUID attachmentId,
        String tenantId,
        String userId,
        String objectKey,
        long size,
        String etag,
        String detectedMediaType) {
}
