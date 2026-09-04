package com.smart.agent.attachment;

import com.smart.agent.knowledge.VectorIndex;
import com.smart.agent.knowledge.VectorNamespace;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean({AttachmentObjectStorage.class, VectorIndex.class})
public class AttachmentCleanupJob {
    private final AttachmentRepository repository;
    private final AttachmentService attachmentService;
    private final VectorIndex vectorIndex;
    private final AttachmentObjectStorage objectStorage;
    private final Clock clock;

    @Autowired
    public AttachmentCleanupJob(AttachmentRepository repository, AttachmentService attachmentService,
            VectorIndex vectorIndex, AttachmentObjectStorage objectStorage) {
        this(repository, attachmentService, vectorIndex, objectStorage, Clock.systemUTC());
    }

    AttachmentCleanupJob(AttachmentRepository repository, AttachmentService attachmentService,
            VectorIndex vectorIndex, AttachmentObjectStorage objectStorage, Clock clock) {
        this.repository = repository;
        this.attachmentService = attachmentService;
        this.vectorIndex = vectorIndex;
        this.objectStorage = objectStorage;
        this.clock = clock;
    }

    public CleanupResult cleanupExpired(Instant lastUsedBefore, int pageSize, int maxPages) {
        if (lastUsedBefore == null || pageSize < 1 || pageSize > 1_000 || maxPages < 1 || maxPages > 1_000) {
            throw new IllegalArgumentException("Invalid cleanup bounds");
        }
        int attempted = 0;
        int completed = 0;
        int failed = 0;
        UUID afterId = null;
        for (int page = 0; page < maxPages; page++) {
            List<Attachment> candidates = repository.findExpiredChatAttachments(lastUsedBefore, afterId, pageSize);
            if (candidates.isEmpty()) break;
            for (Attachment candidate : candidates) {
                attempted++;
                afterId = candidate.id();
                try {
                    if (candidate.status() != AttachmentStatus.EXPIRED) {
                        attachmentService.expire(candidate.id(), candidate.tenantId(), candidate.userId());
                    }
                    vectorIndex.deleteAttachment(
                            VectorNamespace.CHAT_ATTACHMENT, candidate.tenantId(), candidate.id().toString());
                    objectStorage.delete(candidate.objectKey());
                    attachmentService.completeCleanup(candidate.id(), candidate.tenantId(), candidate.userId());
                    completed++;
                } catch (RuntimeException exception) {
                    failed++;
                }
            }
            if (candidates.size() < pageSize) break;
        }
        return new CleanupResult(attempted, completed, failed);
    }

    public CleanupResult cleanupExpiredByDefaultRetention(int pageSize, int maxPages) {
        return cleanupExpired(clock.instant().minus(java.time.Duration.ofDays(7)), pageSize, maxPages);
    }

    public record CleanupResult(int attempted, int completed, int failed) {
    }
}
