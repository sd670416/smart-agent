package com.smart.agent.knowledge.manage;

import com.smart.agent.attachment.Attachment;
import com.smart.agent.attachment.AttachmentPurpose;
import com.smart.agent.attachment.AttachmentRepository;
import com.smart.agent.attachment.AttachmentStatus;
import com.smart.agent.security.AgentUserContext;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeManagementService {
    private static final String MANAGE_PERMISSION = "ai:knowledge:manage";
    private final KnowledgeManagementRepository repository;
    private final AttachmentRepository attachments;
    private final Clock clock;

    @Autowired
    public KnowledgeManagementService(KnowledgeManagementRepository repository, AttachmentRepository attachments) {
        this(repository, attachments, Clock.systemUTC());
    }

    KnowledgeManagementService(KnowledgeManagementRepository repository, AttachmentRepository attachments, Clock clock) {
        this.repository = repository; this.attachments = attachments; this.clock = clock;
    }

    @Transactional
    public KnowledgeSpace createSpace(CreateKnowledgeSpaceCommand command, AgentUserContext actor) {
        requireManager(actor);
        if (command == null || command.scope() == null) throw new IllegalArgumentException("scope is required");
        String projectId = blankToNull(command.projectId());
        if ((command.scope() == KnowledgeScope.PROJECT) != (projectId != null)) {
            throw new IllegalArgumentException("PROJECT scope requires exactly one projectId");
        }
        if (projectId != null && !actor.canAccessProject(projectId)) throw new KnowledgeForbiddenException();
        Instant now = clock.instant();
        return repository.saveSpace(new KnowledgeSpace(UUID.randomUUID(), actor.tenantId(), command.name(),
                blankToNull(command.description()), command.scope(), projectId, KnowledgeStatus.DRAFT,
                actor.userId(), now, actor.userId(), now));
    }

    @Transactional(readOnly = true)
    public KnowledgeSpace getSpace(UUID id, AgentUserContext actor) { return ownedSpace(id, actor); }

    @Transactional(readOnly = true)
    public PageResult<KnowledgeSpace> listSpaces(AgentUserContext actor, int page, int size) {
        requireManager(actor); validatePage(page, size);
        return repository.listSpaces(actor.tenantId(), actor.projectIds(), page, size);
    }

    @Transactional
    public KnowledgeSpace updateSpace(UUID id, String name, String description, AgentUserContext actor) {
        KnowledgeSpace current = ownedSpace(id, actor);
        return repository.saveSpace(current.withDetails(KnowledgeSpace.required(name, "name"),
                blankToNull(description), actor.userId(), clock.instant()));
    }

    @Transactional
    public KnowledgeSpace publishSpace(UUID id, AgentUserContext actor) {
        KnowledgeSpace current = ownedSpace(id, actor);
        if (current.status() != KnowledgeStatus.DRAFT && current.status() != KnowledgeStatus.DISABLED) {
            throw new IllegalStateException("Only draft or disabled spaces can be published");
        }
        return repository.saveSpace(current.withStatus(KnowledgeStatus.PUBLISHED, actor.userId(), clock.instant()));
    }

    @Transactional
    public KnowledgeSpace disableSpace(UUID id, AgentUserContext actor) {
        KnowledgeSpace current = ownedSpace(id, actor);
        if (current.status() != KnowledgeStatus.PUBLISHED) throw new IllegalStateException("Only published spaces can be disabled");
        return repository.saveSpace(current.withStatus(KnowledgeStatus.DISABLED, actor.userId(), clock.instant()));
    }

    @Transactional
    public KnowledgeDocumentVersion attachUploadedDocument(UUID spaceId, UUID attachmentId, AgentUserContext actor) {
        ownedSpace(spaceId, actor);
        Attachment attachment = attachments.findByIdAndTenantIdAndUserId(attachmentId, actor.tenantId(), actor.userId())
                .orElseThrow(KnowledgeNotFoundException::new);
        if (attachment.purpose() != AttachmentPurpose.KNOWLEDGE_DOCUMENT) {
            throw new IllegalArgumentException("Attachment purpose must be KNOWLEDGE_DOCUMENT");
        }
        if (attachment.status() != AttachmentStatus.UPLOADED) {
            throw new IllegalStateException("Knowledge attachment must be uploaded");
        }
        repository.lockAttachment(attachmentId, actor.tenantId(), actor.userId());
        repository.lockSpace(spaceId, actor.tenantId());
        KnowledgeDocumentVersion existing = repository.findVersionByAttachment(attachmentId, actor.tenantId()).orElse(null);
        if (existing != null) {
            ManagedKnowledgeDocument existingDocument = ownedDocument(existing.documentId(), actor);
            if (!existingDocument.spaceId().equals(spaceId)) throw new IllegalStateException("Attachment already belongs to another space");
            return existing;
        }
        Instant now = clock.instant();
        ManagedKnowledgeDocument document = repository.findDocumentBySpaceAndTitle(
                spaceId, attachment.originalFilename(), actor.tenantId()).orElseGet(() -> repository.saveDocument(
                        new ManagedKnowledgeDocument(UUID.randomUUID(), actor.tenantId(), spaceId,
                                attachment.originalFilename(), KnowledgeStatus.DRAFT, null,
                                actor.userId(), now, actor.userId(), now)));
        repository.lockDocument(document.id(), actor.tenantId());
        int versionNo = repository.nextVersionNumber(document.id(), actor.tenantId());
        return repository.saveVersion(new KnowledgeDocumentVersion(UUID.randomUUID(), document.id(), attachment.id(),
                actor.tenantId(), versionNo, KnowledgeStatus.PROCESSING, null, actor.userId(), now, actor.userId(), now));
    }

    @Transactional
    public KnowledgeDocumentVersion markDraft(UUID versionId, AgentUserContext actor) {
        KnowledgeDocumentVersion version = ownedVersion(versionId, actor);
        if (version.status() != KnowledgeStatus.PROCESSING) throw new IllegalStateException("Only processing versions can become draft");
        return repository.saveVersion(version.withStatus(KnowledgeStatus.DRAFT, null, actor.userId(), clock.instant()));
    }

    @Transactional
    public KnowledgeDocumentVersion publishVersion(UUID versionId, AgentUserContext actor) {
        KnowledgeDocumentVersion version = ownedVersion(versionId, actor);
        if (version.status() != KnowledgeStatus.DRAFT) throw new IllegalStateException("Only draft versions can be published");
        return repository.publish(versionId, actor.tenantId(), actor.userId(), clock.instant());
    }

    @Transactional
    public KnowledgeDocumentVersion disableVersion(UUID versionId, AgentUserContext actor) {
        KnowledgeDocumentVersion version = ownedVersion(versionId, actor);
        if (version.status() != KnowledgeStatus.PUBLISHED) throw new IllegalStateException("Only published versions can be disabled");
        return repository.disable(versionId, actor.tenantId(), actor.userId(), clock.instant());
    }

    @Transactional
    public KnowledgeDocumentVersion publishDocument(UUID documentId, AgentUserContext actor) {
        ManagedKnowledgeDocument document = ownedDocument(documentId, actor);
        KnowledgeDocumentVersion latest = repository.findLatestVersion(document.id(), actor.tenantId())
                .orElseThrow(KnowledgeNotFoundException::new);
        return publishVersion(latest.id(), actor);
    }

    @Transactional
    public KnowledgeDocumentVersion disableDocument(UUID documentId, AgentUserContext actor) {
        ManagedKnowledgeDocument document = ownedDocument(documentId, actor);
        KnowledgeDocumentVersion active = repository.findActiveVersion(document.id(), actor.tenantId())
                .orElseThrow(KnowledgeNotFoundException::new);
        return disableVersion(active.id(), actor);
    }

    @Transactional
    public KnowledgeDocumentVersion retryDocument(UUID documentId, AgentUserContext actor) {
        ManagedKnowledgeDocument document = ownedDocument(documentId, actor);
        KnowledgeDocumentVersion latest = repository.findLatestVersion(document.id(), actor.tenantId())
                .orElseThrow(KnowledgeNotFoundException::new);
        if (latest.status() != KnowledgeStatus.FAILED && latest.status() != KnowledgeStatus.UNSUPPORTED) {
            throw new IllegalStateException("Only failed or unsupported versions can be retried");
        }
        return repository.saveVersion(latest.withStatus(KnowledgeStatus.PROCESSING, null, actor.userId(), clock.instant()));
    }

    @Transactional
    public void deleteDocument(UUID documentId, AgentUserContext actor) {
        ManagedKnowledgeDocument document = ownedDocument(documentId, actor);
        repository.findActiveVersion(document.id(), actor.tenantId())
                .ifPresent(version -> repository.disable(version.id(), actor.tenantId(), actor.userId(), clock.instant()));
        repository.deleteDocument(documentId, actor.tenantId(), actor.userId(), clock.instant());
    }

    @Transactional(readOnly = true)
    public ManagedKnowledgeDocument getDocument(UUID id, AgentUserContext actor) { return ownedDocument(id, actor); }

    @Transactional(readOnly = true)
    public PageResult<ManagedKnowledgeDocument> listDocuments(UUID spaceId, AgentUserContext actor, int page, int size) {
        ownedSpace(spaceId, actor); validatePage(page, size);
        return repository.listDocuments(spaceId, actor.tenantId(), page, size);
    }

    public KnowledgeDocumentVersion getVersion(UUID id, AgentUserContext actor) { return ownedVersion(id, actor); }

    private KnowledgeSpace ownedSpace(UUID id, AgentUserContext actor) {
        requireManager(actor);
        if (id == null) throw new IllegalArgumentException("spaceId is required");
        KnowledgeSpace space = repository.findSpace(id, actor.tenantId()).orElseThrow(KnowledgeNotFoundException::new);
        if (space.scope() == KnowledgeScope.PROJECT && !actor.canAccessProject(space.projectId())) throw new KnowledgeForbiddenException();
        return space;
    }
    private ManagedKnowledgeDocument ownedDocument(UUID id, AgentUserContext actor) {
        if (id == null) throw new IllegalArgumentException("documentId is required");
        ManagedKnowledgeDocument document = repository.findDocument(id, actor.tenantId()).orElseThrow(KnowledgeNotFoundException::new);
        ownedSpace(document.spaceId(), actor); return document;
    }
    private KnowledgeDocumentVersion ownedVersion(UUID id, AgentUserContext actor) {
        if (id == null) throw new IllegalArgumentException("versionId is required");
        KnowledgeDocumentVersion version = repository.findVersion(id, actor.tenantId()).orElseThrow(KnowledgeNotFoundException::new);
        ownedDocument(version.documentId(), actor); return version;
    }
    private static void requireManager(AgentUserContext actor) {
        if (actor == null || !actor.permissions().contains(MANAGE_PERMISSION)) throw new KnowledgeForbiddenException();
    }
    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page request");
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
