package com.smart.agent.question;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.conversation.*;
import com.smart.agent.run.*;
import com.smart.agent.security.AgentUserContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QuestionRecordControllerTest {
    private final AgentRunRepository runs=mock(AgentRunRepository.class);
    private final ConversationRepository conversations=mock(ConversationRepository.class);
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    private final AgentUserContext user=new AgentUserContext("tenant","user","identity",Set.of(),Set.of());
    private QuestionRecordService service() {
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new QuestionRecordService(jdbc,runs,conversations,mock(AgentRunStepRepository.class),new ObjectMapper(),manager);
    }
    @Test void deniesManagementWithoutMenuPermission() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new QuestionRecordController(service())).build();
        mvc.perform(get("/agent/question-records/page").requestAttr(AgentUserContext.class.getName(),user)).andExpect(status().isForbidden());
        mvc.perform(get("/agent/question-records/record").requestAttr(AgentUserContext.class.getName(),user)).andExpect(status().isForbidden());
    }
    @Test void rejectsMessageFromAnotherRunEvenInsideOwnConversation() throws Exception {
        AgentRun run=AgentRun.start("tenant","user","conversation"); run.bindAssistantMessage("actual");
        when(runs.findByIdAndTenantIdAndUserId("tenant","user",run.id())).thenReturn(Optional.of(run));
        var mvc=MockMvcBuilders.standaloneSetup(new QuestionRecordController(service())).build();
        mvc.perform(post("/agent/question-records/feedback").requestAttr(AgentUserContext.class.getName(),user)
                .contentType("application/json").content("{\"conversationId\":\"conversation\",\"runId\":\""+run.id()+"\",\"messageId\":\"forged\"}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(jdbc);
    }
    @Test void rejectsCrossUserOrTenantFeedback() throws Exception {
        when(runs.findByIdAndTenantIdAndUserId("tenant","user","foreign")).thenReturn(Optional.empty());
        var mvc=MockMvcBuilders.standaloneSetup(new QuestionRecordController(service())).build();
        mvc.perform(post("/agent/question-records/feedback").requestAttr(AgentUserContext.class.getName(),user)
                .contentType("application/json").content("{\"conversationId\":\"c\",\"runId\":\"foreign\",\"messageId\":\"m\"}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(jdbc);
    }
    @Test void collectionFailureIsIsolated() {
        when(runs.findByIdAndTenantIdAndUserId(any(),any(),any())).thenThrow(new IllegalStateException("database unavailable"));
        service().collectSafely(user,"run","SYSTEM",null,"AGENT_MODEL_FAILED");
    }
    @Test void rejectsUnknownFeedbackFieldsEvenWithLenientJackson() throws Exception {
        var mapper=new ObjectMapper().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        var mvc=MockMvcBuilders.standaloneSetup(new QuestionRecordController(service()))
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(mapper)).build();
        mvc.perform(post("/agent/question-records/feedback").requestAttr(AgentUserContext.class.getName(),user)
                .contentType("application/json").content("{\"conversationId\":\"c\",\"runId\":\"r\",\"messageId\":\"m\",\"question\":\"forged\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void rejectsInvertedDateRanges() throws Exception {
        var managerUser=new AgentUserContext("tenant","user","identity",Set.of("aiQuestion:menu"),Set.of());
        var mvc=MockMvcBuilders.standaloneSetup(new QuestionRecordController(service())).build();
        mvc.perform(get("/agent/question-records/page").param("startDate","2026-10-15").param("endDate","2026-10-14")
                .requestAttr(AgentUserContext.class.getName(),managerUser)).andExpect(status().isBadRequest());
    }
}
