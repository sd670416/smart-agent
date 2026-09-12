package com.smart.agent.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.conversation.Conversation;
import com.smart.agent.conversation.Message;
import com.smart.agent.run.AgentRun;
import com.smart.agent.run.AgentRunStatus;
import com.smart.agent.run.AgentRunStep;
import org.junit.jupiter.api.Test;

class ConversationHistorySerializationTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesRunAndDebugStepFieldsRequiredByConversationHistory() throws Exception {
        AgentRun run = AgentRun.start("tenant", "user", "conversation", "trace-1");
        run.transition(AgentRunStatus.ROUTING);
        AgentRunStep step = AgentRunStep.completed("tenant", "user", run.id(), 1,
                "AI_DEBUG_TOOL_REQUEST", "project.query", "{\"page\":1}");

        String runJson = mapper.writeValueAsString(new ConversationQueryController.RunSummary(run));
        String stepJson = mapper.writeValueAsString(new ConversationQueryController.RunStepSummary(step));

        assertThat(runJson).contains("\"id\":\"" + run.id() + "\"", "\"traceId\":\"trace-1\"");
        assertThat(stepJson).contains("\"type\":\"AI_DEBUG_TOOL_REQUEST\"",
                "\"safeInputSummary\":\"project.query\"", "safeOutputSummary");
    }

    @Test
    void serializesPersistedTimesRequiredByConversationHistoryUi() throws Exception {
        Conversation conversation = Conversation.create("tenant", "user", "title");
        conversation.append(Message.Role.USER, "hello");
        AgentRun run = AgentRun.start("tenant", "user", conversation.id(), "trace-1");

        String conversationJson = mapper.writeValueAsString(conversation);
        String summaryJson = mapper.writeValueAsString(new ConversationQueryController.ConversationSummary(conversation));
        String runJson = mapper.writeValueAsString(new ConversationQueryController.RunSummary(run));

        assertThat(conversationJson).contains("\"createdAt\"");
        assertThat(summaryJson).contains("\"updatedAt\"");
        assertThat(runJson).contains("\"createdAt\"", "\"updatedAt\"");
    }
}
