package com.smart.agent.ingestion;

import com.smart.agent.attachment.Attachment;
import com.smart.agent.attachment.AttachmentObjectStorage;
import com.smart.agent.attachment.AttachmentPurpose;
import com.smart.agent.attachment.AttachmentRepository;
import com.smart.agent.attachment.AttachmentService;
import com.smart.agent.attachment.AttachmentStatus;
import com.smart.agent.knowledge.IngestDocumentCommand;
import com.smart.agent.knowledge.KnowledgeIngestionService;
import com.smart.agent.knowledge.VectorNamespace;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnBean(AttachmentObjectStorage.class)
public class DocumentIngestionJob {
    private final AttachmentRepository attachments;
    private final AttachmentService attachmentService;
    private final AttachmentObjectStorage objectStorage;
    private final DocumentParserRegistry parsers;
    private final KnowledgeIngestionService ingestionService;
    private final ParseLimits limits;

    public DocumentIngestionJob(AttachmentRepository attachments, AttachmentService attachmentService,
            AttachmentObjectStorage objectStorage, DocumentParserRegistry parsers,
            KnowledgeIngestionService ingestionService, ParseLimits limits) {
        this.attachments = attachments;
        this.attachmentService = attachmentService;
        this.objectStorage = objectStorage;
        this.parsers = parsers;
        this.ingestionService = ingestionService;
        this.limits = limits;
    }

    @Async
    @Transactional
    public CompletableFuture<Void> ingest(DocumentIngestionCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        Attachment attachment = attachments.findByIdAndTenantIdAndUserId(
                        command.attachmentId(), command.tenantId(), command.userId())
                .orElseThrow(com.smart.agent.attachment.AttachmentNotFoundException::new);
        if (attachment.status() == AttachmentStatus.READY
                || attachment.status() == AttachmentStatus.UNSUPPORTED
                || attachment.status() == AttachmentStatus.QUARANTINED) {
            return CompletableFuture.completedFuture(null);
        }
        if (attachment.status() == AttachmentStatus.UPLOADED) {
            attachment = attachmentService.markProcessing(
                    attachment.id(), command.tenantId(), command.userId());
        } else if (attachment.status() != AttachmentStatus.PROCESSING) {
            throw new IllegalStateException("Attachment is not ready for ingestion");
        }

        try (InputStream input = objectStorage.open(attachment.objectKey())) {
            DocumentParseResult parsed = parsers.parse(attachment.originalFilename(), input, limits);
            if (parsed.status() == DocumentParseStatus.UNSUPPORTED) {
                attachmentService.markUnsupported(attachment.id(), command.tenantId(), command.userId(),
                        parsed.failureCode());
                return CompletableFuture.completedFuture(null);
            }
            if (parsed.status() == DocumentParseStatus.QUARANTINED) {
                attachmentService.quarantine(attachment.id(), command.tenantId(), command.userId(),
                        parsed.failureCode());
                return CompletableFuture.completedFuture(null);
            }
            if (!parsed.blocks().isEmpty()) {
                VectorNamespace namespace = attachment.purpose() == AttachmentPurpose.KNOWLEDGE_DOCUMENT
                        ? VectorNamespace.KNOWLEDGE : VectorNamespace.CHAT_ATTACHMENT;
                if (namespace == VectorNamespace.CHAT_ATTACHMENT && command.expiresAt() == null) {
                    throw new IllegalArgumentException("expiresAt is required for chat attachments");
                }
                ingestionService.ingestDocument(new IngestDocumentCommand(
                        command.tenantId(), command.spaceId(), command.organizationId(), command.projectId(),
                        command.documentId(), command.documentVersionId(), attachment.id().toString(), command.title(),
                        command.status(), "tika-" + parsed.detectedType().name().toLowerCase(java.util.Locale.ROOT) + "-v1",
                        command.userId(), namespace == VectorNamespace.CHAT_ATTACHMENT ? command.expiresAt() : null),
                        parsed.blocks(), namespace);
            }
            attachmentService.markReady(attachment.id(), command.tenantId(), command.userId());
            return CompletableFuture.completedFuture(null);
        } catch (DocumentParseException exception) {
            attachmentService.fail(attachment.id(), command.tenantId(), command.userId(), exception.code());
            return CompletableFuture.failedFuture(exception);
        } catch (java.io.IOException exception) {
            attachmentService.fail(attachment.id(), command.tenantId(), command.userId(), "OBJECT_READ_FAILED");
            return CompletableFuture.failedFuture(exception);
        }
    }
}
