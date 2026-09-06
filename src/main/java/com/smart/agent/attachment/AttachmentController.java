package com.smart.agent.attachment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smart.agent.common.api.ApiError;
import com.smart.agent.security.AgentUserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent/attachments")
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AttachmentController {
    private static final String CONTEXT_ATTRIBUTE = "com.smart.agent.security.AgentUserContext";
    private final AttachmentService service;

    public AttachmentController(AttachmentService service) {
        this.service = service;
    }

    @PostMapping("/register")
    public AttachmentResponse register(@Valid @RequestBody RegisterRequest request,
            @RequestAttribute(CONTEXT_ATTRIBUTE) AgentUserContext context) {
        return response(service.registerUpload(context.tenantId(), context.userId(), request.purpose(),
                request.originalFilename(), request.expiresAt()));
    }

    @PostMapping("/{id}/complete")
    public AttachmentResponse complete(@PathVariable UUID id, @Valid @RequestBody CompleteRequest request,
            @RequestAttribute(CONTEXT_ATTRIBUTE) AgentUserContext context) {
        return response(service.completeUpload(new CompleteUploadCommand(id, context.tenantId(), context.userId(),
                request.objectKey(), request.size(), request.etag(), request.detectedMediaType())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AttachmentResponse> get(@PathVariable UUID id,
            @RequestAttribute(CONTEXT_ATTRIBUTE) AgentUserContext context) {
        return ResponseEntity.ok(response(service.get(id, context.tenantId(), context.userId())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
            @RequestAttribute(CONTEXT_ATTRIBUTE) AgentUserContext context) {
        service.expire(id, context.tenantId(), context.userId());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(AttachmentNotFoundException.class)
    public ResponseEntity<ApiError> notFound(AttachmentNotFoundException exception) {
        return ResponseEntity.status(404).body(new ApiError("ATTACHMENT_NOT_FOUND", exception.getMessage(), null));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> conflict(IllegalStateException exception) {
        return ResponseEntity.status(409).body(new ApiError("ATTACHMENT_STATE_CONFLICT", exception.getMessage(), null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiError("ATTACHMENT_INVALID", exception.getMessage(), null));
    }

    private static AttachmentResponse response(Attachment attachment) {
        return new AttachmentResponse(attachment.id(), attachment.objectKey(), attachment.purpose(),
                attachment.originalFilename(), attachment.size(), attachment.etag(), attachment.detectedMediaType(),
                attachment.status(), attachment.uploadExpiresAt());
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record RegisterRequest(@NotNull AttachmentPurpose purpose, @NotBlank String originalFilename,
            @NotNull Instant expiresAt) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CompleteRequest(@NotBlank String objectKey, @PositiveOrZero long size, @NotBlank String etag,
            @NotBlank String detectedMediaType) {}

    public record AttachmentResponse(UUID attachmentId, String objectKey, AttachmentPurpose purpose,
            String originalFilename, Long size, String etag, String detectedMediaType, AttachmentStatus status,
            Instant expiresAt) {}
}
