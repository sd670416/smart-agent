package com.smart.agent.knowledge.manage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smart.agent.attachment.Attachment;
import com.smart.agent.attachment.AttachmentPurpose;
import com.smart.agent.attachment.AttachmentRepository;
import com.smart.agent.attachment.AttachmentStatus;
import com.smart.agent.security.AgentUserContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeManagementServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-04T10:00:00Z");
    private final MemoryRepository repository = new MemoryRepository();
    private final AttachmentRepository attachments = mock(AttachmentRepository.class);
    private final AgentUserContext actor = new AgentUserContext(
            "tenant-1", "user-1", "identity-1", Set.of("ai:knowledge:manage"), Set.of("project-1"));
    private KnowledgeManagementService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeManagementService(repository, attachments,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void projectSpaceRequiresExactlyOneProjectIdAndProjectAccess() {
        assertThatThrownBy(() -> service.createSpace(
                new CreateKnowledgeSpaceCommand("项目资料", null, KnowledgeScope.PROJECT, null), actor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createSpace(
                new CreateKnowledgeSpaceCommand("租户资料", null, KnowledgeScope.TENANT, "project-1"), actor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createSpace(
                new CreateKnowledgeSpaceCommand("越权项目", null, KnowledgeScope.PROJECT, "project-2"), actor))
                .isInstanceOf(KnowledgeForbiddenException.class);

        KnowledgeSpace space = service.createSpace(
                new CreateKnowledgeSpaceCommand("项目资料", "施工规范", KnowledgeScope.PROJECT, "project-1"), actor);
        assertThat(space.scope()).isEqualTo(KnowledgeScope.PROJECT);
        assertThat(space.projectId()).isEqualTo("project-1");
        assertThat(space.status()).isEqualTo(KnowledgeStatus.DRAFT);
    }

    @Test
    void uploadedKnowledgeAttachmentCreatesIncreasingDraftVersions() {
        KnowledgeSpace space = service.createSpace(
                new CreateKnowledgeSpaceCommand("公共资料", null, KnowledgeScope.TENANT, null), actor);
        UUID firstAttachment = uploadedKnowledgeAttachment("tenant-1", "user-1", "规范.pdf");
        UUID secondAttachment = uploadedKnowledgeAttachment("tenant-1", "user-1", "规范.pdf");

        KnowledgeDocumentVersion first = service.attachUploadedDocument(space.id(), firstAttachment, actor);
        KnowledgeDocumentVersion second = service.attachUploadedDocument(space.id(), secondAttachment, actor);

        assertThat(first.versionNo()).isEqualTo(1);
        assertThat(second.versionNo()).isEqualTo(2);
        assertThat(second.documentId()).isEqualTo(first.documentId());
        assertThat(second.status()).isEqualTo(KnowledgeStatus.PROCESSING);
    }

    @Test
    void rejectsWrongTenantNonKnowledgeAndIncompleteAttachments() {
        KnowledgeSpace space = service.createSpace(
                new CreateKnowledgeSpaceCommand("公共资料", null, KnowledgeScope.TENANT, null), actor);
        UUID wrongTenant = uploadedKnowledgeAttachment("tenant-2", "user-1", "a.pdf");
        UUID chat = attachment("tenant-1", "user-1", "a.pdf", AttachmentPurpose.CHAT_ATTACHMENT,
                AttachmentStatus.UPLOADED);
        UUID pending = attachment("tenant-1", "user-1", "a.pdf", AttachmentPurpose.KNOWLEDGE_DOCUMENT,
                AttachmentStatus.PENDING_UPLOAD);

        assertThatThrownBy(() -> service.attachUploadedDocument(space.id(), wrongTenant, actor))
                .isInstanceOf(KnowledgeNotFoundException.class);
        assertThatThrownBy(() -> service.attachUploadedDocument(space.id(), chat, actor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.attachUploadedDocument(space.id(), pending, actor))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void repeatedAttachmentCompletionIsIdempotent() {
        KnowledgeSpace space = service.createSpace(
                new CreateKnowledgeSpaceCommand("公共资料", null, KnowledgeScope.TENANT, null), actor);
        UUID attachment = uploadedKnowledgeAttachment("tenant-1", "user-1", "规范.pdf");

        KnowledgeDocumentVersion first = service.attachUploadedDocument(space.id(), attachment, actor);
        KnowledgeDocumentVersion repeated = service.attachUploadedDocument(space.id(), attachment, actor);

        assertThat(repeated).isEqualTo(first);
        assertThat(repository.versions).hasSize(1);
    }

    @Test
    void projectAccessIsCheckedOnEveryRead() {
        KnowledgeSpace space = service.createSpace(
                new CreateKnowledgeSpaceCommand("项目资料", null, KnowledgeScope.PROJECT, "project-1"), actor);
        AgentUserContext revoked = new AgentUserContext("tenant-1", "user-1", "identity-1",
                Set.of("ai:knowledge:manage"), Set.of());

        assertThatThrownBy(() -> service.getSpace(space.id(), revoked))
                .isInstanceOf(KnowledgeForbiddenException.class);
        assertThat(service.listSpaces(revoked, 0, 20).items()).isEmpty();
    }

    @Test
    void publishingDraftAtomicallyReplacesPreviouslyPublishedVersion() {
        KnowledgeSpace space = service.createSpace(
                new CreateKnowledgeSpaceCommand("公共资料", null, KnowledgeScope.TENANT, null), actor);
        KnowledgeDocumentVersion first = service.attachUploadedDocument(
                space.id(), uploadedKnowledgeAttachment("tenant-1", "user-1", "规范.pdf"), actor);
        KnowledgeDocumentVersion second = service.attachUploadedDocument(
                space.id(), uploadedKnowledgeAttachment("tenant-1", "user-1", "规范.pdf"), actor);

        service.markDraft(first.id(), actor); service.markDraft(second.id(), actor);
        service.publishVersion(first.id(), actor);
        KnowledgeDocumentVersion published = service.publishVersion(second.id(), actor);

        assertThat(published.status()).isEqualTo(KnowledgeStatus.PUBLISHED);
        assertThat(repository.versions.get(first.id()).status()).isEqualTo(KnowledgeStatus.DISABLED);
        assertThat(repository.activeVersions.get(first.documentId())).isEqualTo(second.id());
        assertThatThrownBy(() -> service.publishVersion(second.id(), actor))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unsupportedAndQuarantinedVersionsCannotBePublished() {
        repository.versions.put(UUID.fromString("00000000-0000-0000-0000-000000000011"), version(KnowledgeStatus.UNSUPPORTED));
        repository.versions.put(UUID.fromString("00000000-0000-0000-0000-000000000012"), version(KnowledgeStatus.QUARANTINED));

        assertThatThrownBy(() -> service.publishVersion(
                UUID.fromString("00000000-0000-0000-0000-000000000011"), actor)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.publishVersion(
                UUID.fromString("00000000-0000-0000-0000-000000000012"), actor)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deleteDisablesPublishedVersionBeforeMarkingDocumentDeleted() {
        KnowledgeSpace space = service.createSpace(
                new CreateKnowledgeSpaceCommand("公共资料", null, KnowledgeScope.TENANT, null), actor);
        KnowledgeDocumentVersion draft = service.attachUploadedDocument(
                space.id(), uploadedKnowledgeAttachment("tenant-1", "user-1", "规范.pdf"), actor);
        service.markDraft(draft.id(), actor); service.publishVersion(draft.id(), actor);

        service.deleteDocument(draft.documentId(), actor);

        assertThat(repository.events).containsExactly("disable:" + draft.id(), "delete:" + draft.documentId());
        assertThat(repository.documents.get(draft.documentId()).status()).isEqualTo(KnowledgeStatus.DELETED);
    }

    @Test
    void tenantIsolationReturnsNotFound() {
        KnowledgeSpace space = service.createSpace(
                new CreateKnowledgeSpaceCommand("公共资料", null, KnowledgeScope.TENANT, null), actor);
        AgentUserContext other = new AgentUserContext("tenant-2", "user-2", "identity-2",
                Set.of("ai:knowledge:manage"), Set.of());

        assertThatThrownBy(() -> service.getSpace(space.id(), other)).isInstanceOf(KnowledgeNotFoundException.class);
        assertThat(service.listSpaces(other, 0, 20).items()).isEmpty();
    }

    private UUID uploadedKnowledgeAttachment(String tenant, String user, String filename) {
        return attachment(tenant, user, filename, AttachmentPurpose.KNOWLEDGE_DOCUMENT, AttachmentStatus.UPLOADED);
    }

    private UUID attachment(String tenant, String user, String filename, AttachmentPurpose purpose,
            AttachmentStatus status) {
        UUID id = UUID.randomUUID();
        Attachment value = mock(Attachment.class);
        when(value.id()).thenReturn(id);
        when(value.tenantId()).thenReturn(tenant);
        when(value.userId()).thenReturn(user);
        when(value.originalFilename()).thenReturn(filename);
        when(value.purpose()).thenReturn(purpose);
        when(value.status()).thenReturn(status);
        when(attachments.findByIdAndTenantIdAndUserId(id, tenant, user)).thenReturn(Optional.of(value));
        return id;
    }

    private KnowledgeDocumentVersion version(KnowledgeStatus status) {
        KnowledgeSpace space = service.createSpace(
                new CreateKnowledgeSpaceCommand("测试空间" + UUID.randomUUID(), null, KnowledgeScope.TENANT, null), actor);
        UUID documentId = UUID.randomUUID();
        repository.saveDocument(new ManagedKnowledgeDocument(documentId, "tenant-1", space.id(), "测试文档",
                KnowledgeStatus.DRAFT, null, "user-1", NOW, "user-1", NOW));
        return new KnowledgeDocumentVersion(UUID.randomUUID(), documentId, UUID.randomUUID(), "tenant-1",
                1, status, null, "user-1", NOW, NOW);
    }

    private static final class MemoryRepository implements KnowledgeManagementRepository {
        private final Map<UUID, KnowledgeSpace> spaces = new LinkedHashMap<>();
        private final Map<UUID, ManagedKnowledgeDocument> documents = new LinkedHashMap<>();
        private final Map<UUID, KnowledgeDocumentVersion> versions = new LinkedHashMap<>();
        private final Map<UUID, UUID> activeVersions = new LinkedHashMap<>();
        private final List<String> events = new ArrayList<>();

        public KnowledgeSpace saveSpace(KnowledgeSpace space) { spaces.put(space.id(), space); return space; }
        public Optional<KnowledgeSpace> findSpace(UUID id, String tenant) {
            return Optional.ofNullable(spaces.get(id)).filter(s -> s.tenantId().equals(tenant));
        }
        public void lockSpace(UUID spaceId, String tenant) { }
        public void lockAttachment(UUID attachmentId, String tenant, String userId) { }
        public PageResult<KnowledgeSpace> listSpaces(String tenant, Set<String> projectIds, int page, int size) {
            List<KnowledgeSpace> values = spaces.values().stream().filter(s -> s.tenantId().equals(tenant))
                    .filter(s -> s.scope() == KnowledgeScope.TENANT || projectIds.contains(s.projectId())).toList();
            return new PageResult<>(values, values.size(), page, size);
        }
        public Optional<ManagedKnowledgeDocument> findDocument(UUID id, String tenant) {
            return Optional.ofNullable(documents.get(id)).filter(d -> d.tenantId().equals(tenant));
        }
        public Optional<ManagedKnowledgeDocument> findDocumentBySpaceAndTitle(UUID spaceId, String title, String tenant) {
            return documents.values().stream().filter(d -> d.spaceId().equals(spaceId)
                    && d.title().equals(title) && d.tenantId().equals(tenant) && d.status() != KnowledgeStatus.DELETED).findFirst();
        }
        public ManagedKnowledgeDocument saveDocument(ManagedKnowledgeDocument document) {
            documents.put(document.id(), document); return document;
        }
        public int nextVersionNumber(UUID documentId, String tenant) {
            return versions.values().stream().filter(v -> v.documentId().equals(documentId)
                    && v.tenantId().equals(tenant)).mapToInt(KnowledgeDocumentVersion::versionNo).max().orElse(0) + 1;
        }
        public void lockDocument(UUID documentId, String tenant) { }
        public KnowledgeDocumentVersion saveVersion(KnowledgeDocumentVersion version) {
            versions.put(version.id(), version); return version;
        }
        public Optional<KnowledgeDocumentVersion> findVersion(UUID id, String tenant) {
            return Optional.ofNullable(versions.get(id)).filter(v -> v.tenantId().equals(tenant));
        }
        public Optional<KnowledgeDocumentVersion> findVersionByAttachment(UUID attachmentId, String tenant) {
            return versions.values().stream().filter(v -> attachmentId.equals(v.attachmentId())
                    && tenant.equals(v.tenantId())).findFirst();
        }
        public Optional<KnowledgeDocumentVersion> findActiveVersion(UUID documentId, String tenant) {
            return Optional.ofNullable(activeVersions.get(documentId)).flatMap(id -> findVersion(id, tenant));
        }
        public Optional<KnowledgeDocumentVersion> findLatestVersion(UUID documentId, String tenant) {
            return versions.values().stream().filter(v -> v.documentId().equals(documentId)
                    && v.tenantId().equals(tenant)).max(java.util.Comparator.comparingInt(KnowledgeDocumentVersion::versionNo));
        }
        public KnowledgeDocumentVersion publish(UUID id, String tenant, String actor, Instant now) {
            KnowledgeDocumentVersion target = findVersion(id, tenant).orElseThrow();
            UUID old = activeVersions.get(target.documentId());
            if (old != null && !old.equals(id)) disable(old, tenant, actor, now);
            KnowledgeDocumentVersion next = target.withStatus(KnowledgeStatus.PUBLISHED, null, actor, now);
            versions.put(id, next); activeVersions.put(target.documentId(), id); return next;
        }
        public KnowledgeDocumentVersion disable(UUID id, String tenant, String actor, Instant now) {
            KnowledgeDocumentVersion current = findVersion(id, tenant).orElseThrow();
            KnowledgeDocumentVersion next = current.withStatus(KnowledgeStatus.DISABLED, current.failureCode(), actor, now);
            versions.put(id, next); events.add("disable:" + id); return next;
        }
        public void deleteDocument(UUID id, String tenant, String actor, Instant now) {
            ManagedKnowledgeDocument current = findDocument(id, tenant).orElseThrow();
            documents.put(id, current.withStatus(KnowledgeStatus.DELETED, actor, now)); events.add("delete:" + id);
        }
        public PageResult<ManagedKnowledgeDocument> listDocuments(UUID spaceId, String tenant, int page, int size) {
            List<ManagedKnowledgeDocument> values = documents.values().stream().filter(d -> d.spaceId().equals(spaceId)
                    && d.tenantId().equals(tenant) && d.status() != KnowledgeStatus.DELETED).toList();
            return new PageResult<>(values, values.size(), page, size);
        }
    }
}
