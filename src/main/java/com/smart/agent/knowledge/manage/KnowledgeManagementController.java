package com.smart.agent.knowledge.manage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smart.agent.common.api.ApiError;
import com.smart.agent.security.AgentUserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeManagementController {
    private static final String CONTEXT = "com.smart.agent.security.AgentUserContext";
    private final KnowledgeManagementService service;

    public KnowledgeManagementController(KnowledgeManagementService service) { this.service = service; }

    @PostMapping("/knowledge-spaces")
    public SpaceResponse create(@Valid @RequestBody SpaceRequest request,
            @RequestAttribute(CONTEXT) AgentUserContext actor) {
        return space(service.createSpace(new CreateKnowledgeSpaceCommand(request.name(), request.description(),
                request.scope(), request.projectId()), actor));
    }

    @GetMapping("/knowledge-spaces")
    public PageResult<SpaceResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestAttribute(CONTEXT) AgentUserContext actor) {
        PageResult<KnowledgeSpace> result = service.listSpaces(actor, page, size);
        return new PageResult<>(result.items().stream().map(KnowledgeManagementController::space).toList(),
                result.total(), result.page(), result.size());
    }

    @GetMapping("/knowledge-spaces/{id}")
    public SpaceResponse getSpace(@PathVariable UUID id, @RequestAttribute(CONTEXT) AgentUserContext actor) {
        return space(service.getSpace(id, actor));
    }

    @PutMapping("/knowledge-spaces/{id}")
    public SpaceResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateSpaceRequest request,
            @RequestAttribute(CONTEXT) AgentUserContext actor) {
        return space(service.updateSpace(id, request.name(), request.description(), actor));
    }

    @PostMapping("/knowledge-spaces/{id}/update")
    public SpaceResponse updateInternal(@PathVariable UUID id, @Valid @RequestBody UpdateSpaceRequest request,
            @RequestAttribute(CONTEXT) AgentUserContext actor) {
        return update(id, request, actor);
    }

    @PostMapping("/knowledge-spaces/{id}/publish")
    public SpaceResponse publishSpace(@PathVariable UUID id, @RequestAttribute(CONTEXT) AgentUserContext actor) {
        return space(service.publishSpace(id, actor));
    }

    @PostMapping("/knowledge-spaces/{id}/disable")
    public SpaceResponse disableSpace(@PathVariable UUID id, @RequestAttribute(CONTEXT) AgentUserContext actor) {
        return space(service.disableSpace(id, actor));
    }

    @PostMapping("/knowledge-spaces/{id}/documents")
    public VersionResponse attach(@PathVariable UUID id, @Valid @RequestBody AttachRequest request,
            @RequestAttribute(CONTEXT) AgentUserContext actor) {
        return version(service.attachUploadedDocument(id, request.attachmentId(), actor));
    }

    @GetMapping("/knowledge-spaces/{id}/documents")
    public PageResult<DocumentResponse> documents(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(CONTEXT) AgentUserContext actor) {
        PageResult<ManagedKnowledgeDocument> result = service.listDocuments(id, actor, page, size);
        return new PageResult<>(result.items().stream().map(KnowledgeManagementController::document).toList(),
                result.total(), result.page(), result.size());
    }

    @GetMapping("/knowledge-documents/{id}")
    public DocumentResponse document(@PathVariable UUID id, @RequestAttribute(CONTEXT) AgentUserContext actor) {
        return document(service.getDocument(id, actor));
    }

    @PostMapping("/knowledge-versions/{id}/publish")
    public VersionResponse publishVersion(@PathVariable UUID id, @RequestAttribute(CONTEXT) AgentUserContext actor) {
        return version(service.publishVersion(id, actor));
    }

    @PostMapping("/knowledge-versions/{id}/disable")
    public VersionResponse disableVersion(@PathVariable UUID id, @RequestAttribute(CONTEXT) AgentUserContext actor) {
        return version(service.disableVersion(id, actor));
    }

    @PostMapping("/knowledge-documents/{id}/publish")
    public VersionResponse publishDocument(@PathVariable UUID id, @RequestAttribute(CONTEXT) AgentUserContext actor) {
        return version(service.publishDocument(id, actor));
    }

    @PostMapping("/knowledge-documents/{id}/disable")
    public VersionResponse disableDocument(@PathVariable UUID id, @RequestAttribute(CONTEXT) AgentUserContext actor) {
        return version(service.disableDocument(id, actor));
    }

    @PostMapping("/knowledge-documents/{id}/retry")
    public VersionResponse retryDocument(@PathVariable UUID id, @RequestAttribute(CONTEXT) AgentUserContext actor) {
        return version(service.retryDocument(id, actor));
    }

    @GetMapping("/knowledge-versions/{id}")
    public VersionResponse version(@PathVariable UUID id, @RequestAttribute(CONTEXT) AgentUserContext actor) {
        return version(service.getVersion(id, actor));
    }

    @DeleteMapping("/knowledge-documents/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestAttribute(CONTEXT) AgentUserContext actor) {
        service.deleteDocument(id, actor); return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(KnowledgeNotFoundException.class)
    ResponseEntity<ApiError> notFound(KnowledgeNotFoundException exception) {
        return ResponseEntity.status(404).body(new ApiError("KNOWLEDGE_NOT_FOUND", exception.getMessage(), null));
    }
    @ExceptionHandler(KnowledgeForbiddenException.class)
    ResponseEntity<ApiError> forbidden(KnowledgeForbiddenException exception) {
        return ResponseEntity.status(403).body(new ApiError("KNOWLEDGE_FORBIDDEN", exception.getMessage(), null));
    }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> conflict(IllegalStateException exception) {
        return ResponseEntity.status(409).body(new ApiError("KNOWLEDGE_STATE_CONFLICT", exception.getMessage(), null));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiError("KNOWLEDGE_INVALID", exception.getMessage(), null));
    }

    private static SpaceResponse space(KnowledgeSpace value) {
        return new SpaceResponse(value.id(), value.name(), value.description(), value.scope(), value.projectId(),
                value.status(), value.createdBy(), value.createdAt(), value.updatedBy(), value.updatedAt());
    }
    private static DocumentResponse document(ManagedKnowledgeDocument value) {
        return new DocumentResponse(value.id(), value.spaceId(), value.title(), value.status(), value.activeVersionId(),
                value.createdBy(), value.createdAt(), value.updatedBy(), value.updatedAt());
    }
    private static VersionResponse version(KnowledgeDocumentVersion value) {
        return new VersionResponse(value.id(), value.documentId(), value.attachmentId(), value.versionNo(),
                value.status(), value.failureCode(), value.createdBy(), value.createdAt(), value.updatedBy(), value.updatedAt());
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record SpaceRequest(@NotBlank String name, String description, @NotNull KnowledgeScope scope, String projectId) {}
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record UpdateSpaceRequest(@NotBlank String name, String description) {}
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record AttachRequest(@NotNull UUID attachmentId) {}
    public record SpaceResponse(UUID id, String name, String description, KnowledgeScope scope, String projectId,
            KnowledgeStatus status, String createdBy, java.time.Instant createdAt, String updatedBy,
            java.time.Instant updatedAt) {}
    public record DocumentResponse(UUID id, UUID spaceId, String title, KnowledgeStatus status, UUID activeVersionId,
            String createdBy, java.time.Instant createdAt, String updatedBy, java.time.Instant updatedAt) {}
    public record VersionResponse(UUID id, UUID documentId, UUID attachmentId, int versionNo,
            KnowledgeStatus status, String failureCode, String createdBy, java.time.Instant createdAt,
            String updatedBy, java.time.Instant updatedAt) {}
}
