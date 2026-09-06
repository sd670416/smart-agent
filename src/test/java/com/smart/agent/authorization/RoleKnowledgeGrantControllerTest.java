package com.smart.agent.authorization;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RoleKnowledgeGrantControllerTest {
    private final RoleKnowledgeGrantService service = mock(RoleKnowledgeGrantService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new RoleKnowledgeGrantController(service)).build();
    private final UUID space = UUID.randomUUID();
    private AgentUserContext actor(boolean manage) { return new AgentUserContext("t1", "u1", "i1", manage ? Set.of("ai:knowledge:manage") : Set.of(), Set.of()); }

    @Test void getAndPutUseService() throws Exception {
        when(service.grantsForRole("t1", "r1")).thenReturn(Set.of(space));
        mvc.perform(get("/ai/roles/r1/knowledge-grants").requestAttr(AgentUserContext.class.getName(), actor(true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0]").value(space.toString()));
        when(service.replaceGrants(eq("t1"), eq("r1"), anySet(), any())).thenReturn(Set.of(space));
        mvc.perform(put("/ai/roles/r1/knowledge-grants").requestAttr(AgentUserContext.class.getName(), actor(true))
                .contentType("application/json").content(new ObjectMapper().writeValueAsString(Set.of(space))))
                .andExpect(status().isOk());
    }

    @Test void rejectsWithoutManagePermission() throws Exception {
        mvc.perform(get("/ai/roles/r1/knowledge-grants").requestAttr(AgentUserContext.class.getName(), actor(false)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
