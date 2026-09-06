package com.smart.agent.attachment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository {
    Attachment save(Attachment attachment);

    Optional<Attachment> findByIdAndTenantIdAndUserId(UUID id, String tenantId, String userId);

    default List<Attachment> findExpiredChatAttachments(Instant lastUsedBefore, UUID afterId, int limit) {
        return List.of();
    }
}
