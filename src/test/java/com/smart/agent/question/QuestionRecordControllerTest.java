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
    @Test void managementWritesValidatePermissionAndInputBeforeTouchingDatabase() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new QuestionRecordController(service())).build();
        var admin=new AgentUserContext("tenant","user","identity",Set.of("aiQuestion:menu"),Set.of());
        mvc.perform(post("/agent/question-records/id/description").requestAttr(AgentUserContext.class.getName(),user)
                .contentType("application/json").content("{\"description\":\"test\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/agent/question-records/id/status").requestAttr(AgentUserContext.class.getName(),admin)
                .contentType("application/json").content("{\"handlingStatus\":null}")).andExpect(status().isBadRequest());
        mvc.perform(post("/agent/question-records/id/status").requestAttr(AgentUserContext.class.getName(),admin)
                .contentType("application/json").content("{\"handlingStatus\":\"INVALID\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/agent/question-records/id/description").requestAttr(AgentUserContext.class.getName(),admin)
                .contentType("application/json").content(new ObjectMapper().writeValueAsString(Map.of("description","x".repeat(2001)))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(jdbc);
    }
    @Test void missingRecordUpdatesAreScopedToTenant() throws Exception {
        var admin=new AgentUserContext("tenant","user","identity",Set.of("aiQuestion:menu"),Set.of());
        var mvc=MockMvcBuilders.standaloneSetup(new QuestionRecordController(service())).build();
        mvc.perform(post("/agent/question-records/foreign/status").requestAttr(AgentUserContext.class.getName(),admin)
                .contentType("application/json").content("{\"handlingStatus\":\"RESOLVED\"}")).andExpect(status().isNotFound());
        verify(jdbc).update("UPDATE ai_question_record SET handling_status=? WHERE tenant_id=? AND id=?","RESOLVED","tenant","foreign");
    }
    @Test void resolvedUserIdsFilterBeforeCountingAndPagination() throws Exception {
        var admin=new AgentUserContext("tenant","user","identity",Set.of("aiQuestion:menu"),Set.of());
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(0L);
        var mvc=MockMvcBuilders.standaloneSetup(new QuestionRecordController(service())).build();
        mvc.perform(get("/agent/question-records/page").param("userIds","u1,u2")
                .requestAttr(AgentUserContext.class.getName(),admin)).andExpect(status().isOk());
        verify(jdbc).queryForObject("SELECT COUNT(*) FROM ai_question_record WHERE tenant_id=? AND user_id IN (?,?)",Long.class,"tenant","u1","u2");
    }
    @Test void nameResolvedIdsCanBePostedInSearchBody() throws Exception {
        var admin=new AgentUserContext("tenant","user","identity",Set.of("aiQuestion:menu"),Set.of());
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(0L);
        var mvc=MockMvcBuilders.standaloneSetup(new QuestionRecordController(service())).build();
        mvc.perform(post("/agent/question-records/search").contentType("application/json")
                .content("{\"userIds\":[\"u1\",\"u2\"],\"page\":0,\"size\":20}")
                .requestAttr(AgentUserContext.class.getName(),admin)).andExpect(status().isOk());
        verify(jdbc).queryForObject("SELECT COUNT(*) FROM ai_question_record WHERE tenant_id=? AND user_id IN (?,?)",Long.class,"tenant","u1","u2");
    }
    @Test void postedSearchEnforcesPermissionAndDateValidation() throws Exception {
        var admin=new AgentUserContext("tenant","user","identity",Set.of("aiQuestion:menu"),Set.of());
        var mvc=MockMvcBuilders.standaloneSetup(new QuestionRecordController(service())).build();
        String body="{\"userIds\":[\"u1\"],\"page\":0,\"size\":20,\"startDate\":\"2026-10-15\",\"endDate\":\"2026-10-14\"}";
        mvc.perform(post("/agent/question-records/search").contentType("application/json").content(body)
                .requestAttr(AgentUserContext.class.getName(),user)).andExpect(status().isForbidden());
        mvc.perform(post("/agent/question-records/search").contentType("application/json").content(body)
                .requestAttr(AgentUserContext.class.getName(),admin)).andExpect(status().isBadRequest());
        verifyNoInteractions(jdbc);
    }
}
