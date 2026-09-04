package com.smart.agent.attachment;

import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository {
    Attachment save(Attachment attachment);

    Optional<Attachment> findByIdAndTenantIdAndUserId(UUID id, String tenantId, String userId);
}
