package com.smart.agent.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.security.AgentUserContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AttachmentControllerTest {
    private static final Instant NOW = Instant.parse("2026-09-04T10:00:00Z");
    private static final String CONTEXT_ATTRIBUTE = AgentUserContext.class.getName();

    private MockMvc mvc;
    private InMemoryAttachmentRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAttachmentRepository();
        AttachmentService service = new AttachmentService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mvc = MockMvcBuilders.standaloneSetup(new AttachmentController(service))
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void registersCompletesReadsAndExpiresOwnedAttachment() throws Exception {
        String body = mvc.perform(post("/agent/attachments/register")
                        .requestAttr(CONTEXT_ATTRIBUTE, context("tenant-1", "user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"CHAT_ATTACHMENT\",\"originalFilename\":\"photo.png\","
                                + "\"expiresAt\":\"2026-09-04T10:05:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachmentId").isNotEmpty())
                .andExpect(jsonPath("$.objectKey").value(org.hamcrest.Matchers.matchesPattern(
                        "ai/tenant-1/chat-attachment/2026/09/04/user-1/[0-9a-f-]{36}\\.png")))
                .andReturn().getResponse().getContentAsString();
        String id = new ObjectMapper().readTree(body).get("attachmentId").asText();
        String objectKey = repository.values.get(UUID.fromString(id)).objectKey();

        mvc.perform(post("/agent/attachments/{id}/complete", id)
                        .requestAttr(CONTEXT_ATTRIBUTE, context("tenant-1", "user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectKey\":\"" + objectKey + "\",\"size\":12,"
                                + "\"etag\":\"etag-1\",\"detectedMediaType\":\"image/png\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UPLOADED"));
        mvc.perform(get("/agent/attachments/{id}", id)
                        .requestAttr(CONTEXT_ATTRIBUTE, context("tenant-1", "user-1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etag").value("etag-1"));
        mvc.perform(delete("/agent/attachments/{id}", id)
                        .requestAttr(CONTEXT_ATTRIBUTE, context("tenant-1", "user-1")))
                .andExpect(status().isNoContent());
        assertThat(repository.values.get(UUID.fromString(id)).status()).isEqualTo(AttachmentStatus.EXPIRED);
    }

    @Test
    void rejectsClientOwnedSecurityFieldsAndCrossTenantReads() throws Exception {
        mvc.perform(post("/agent/attachments/register")
                        .requestAttr(CONTEXT_ATTRIBUTE, context("tenant-1", "user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"CHAT_ATTACHMENT\",\"originalFilename\":\"x\","
                                + "\"expiresAt\":\"2026-09-04T10:05:00Z\",\"tenantId\":\"tenant-2\"}"))
                .andExpect(status().isBadRequest());

        Attachment attachment = repository.save(Attachment.register(UUID.randomUUID(), "tenant-1", "user-1",
                AttachmentPurpose.CHAT_ATTACHMENT, "x", "ai/key", NOW.plusSeconds(300), NOW));
        mvc.perform(get("/agent/attachments/{id}", attachment.id())
                        .requestAttr(CONTEXT_ATTRIBUTE, context("tenant-2", "user-1")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/agent/attachments/{id}/complete", attachment.id())
                        .requestAttr(CONTEXT_ATTRIBUTE, context("tenant-2", "user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectKey\":\"ai/key\",\"size\":1,\"etag\":\"e\","
                                + "\"detectedMediaType\":\"text/plain\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/agent/attachments/{id}", attachment.id())
                        .requestAttr(CONTEXT_ATTRIBUTE, context("tenant-2", "user-1")))
                .andExpect(status().isNotFound());
    }

    @Test
    void mapsInvalidLifecycleTransitionToConflict() throws Exception {
        Attachment attachment = repository.save(Attachment.register(UUID.randomUUID(), "tenant-1", "user-1",
                AttachmentPurpose.CHAT_ATTACHMENT, "x", "ai/key", NOW.plusSeconds(300), NOW));
        repository.values.get(attachment.id()).expire(NOW);

        mvc.perform(post("/agent/attachments/{id}/complete", attachment.id())
                        .requestAttr(CONTEXT_ATTRIBUTE, context("tenant-1", "user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectKey\":\"ai/key\",\"size\":1,\"etag\":\"e\","
                                + "\"detectedMediaType\":\"text/plain\"}"))
                .andExpect(status().isConflict());
    }

    private AgentUserContext context(String tenantId, String userId) {
        return new AgentUserContext(tenantId, userId, "identity-1", Set.of("role-1"));
    }

    private static final class InMemoryAttachmentRepository implements AttachmentRepository {
        private final Map<UUID, Attachment> values = new LinkedHashMap<>();

        @Override public Attachment save(Attachment attachment) { values.put(attachment.id(), attachment); return attachment; }
        @Override public Optional<Attachment> findByIdAndTenantIdAndUserId(UUID id, String tenantId, String userId) {
            return Optional.ofNullable(values.get(id))
                    .filter(value -> value.tenantId().equals(tenantId) && value.userId().equals(userId));
        }
    }
}
