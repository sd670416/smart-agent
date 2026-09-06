package com.smart.agent.knowledge.manage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.agent.security.AgentUserContext;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class KnowledgeManagementControllerTest {
    private final KnowledgeManagementService service = org.mockito.Mockito.mock(KnowledgeManagementService.class);
    private final AgentUserContext actor = new AgentUserContext(
            "tenant-1", "user-1", "identity-1", Set.of(), Set.of("project-1"));
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mvc = MockMvcBuilders.standaloneSetup(new KnowledgeManagementController(service))
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void createsSpaceUsingTrustedContextAndRejectsIdentityFields() throws Exception {
        KnowledgeSpace space = new KnowledgeSpace(UUID.randomUUID(), "tenant-1", "资料库", null,
                KnowledgeScope.TENANT, null, KnowledgeStatus.DRAFT, "user-1", Instant.now(), "user-1", Instant.now());
        when(service.createSpace(any(), any())).thenReturn(space);

        mvc.perform(post("/agent/knowledge-spaces").requestAttr(AgentUserContext.class.getName(), actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"资料库\",\"scope\":\"TENANT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").doesNotExist())
                .andExpect(jsonPath("$.name").value("资料库"));
        mvc.perform(post("/agent/knowledge-spaces").requestAttr(AgentUserContext.class.getName(), actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"资料库\",\"scope\":\"TENANT\",\"tenantId\":\"tenant-2\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsPagedSpacesAndStableNotFoundError() throws Exception {
        when(service.listSpaces(actor, 0, 20)).thenReturn(new PageResult<>(List.of(), 0, 0, 20));
        when(service.getSpace(any(), any())).thenThrow(new KnowledgeNotFoundException());

        mvc.perform(get("/agent/knowledge-spaces").requestAttr(AgentUserContext.class.getName(), actor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/agent/knowledge-spaces/{id}", UUID.randomUUID())
                .requestAttr(AgentUserContext.class.getName(), actor))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("KNOWLEDGE_NOT_FOUND"));
    }
}
