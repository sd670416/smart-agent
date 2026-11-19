package com.smart.agent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.conversation.ConversationService;
import com.smart.agent.model.ModelGateway;
import com.smart.agent.run.AgentRunService;
import com.smart.agent.tool.ToolExecutor;
import com.smart.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

class AnswerLengthConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withBean(ChatOrchestrator.class, () -> new ChatOrchestrator(
                    mock(ConversationService.class), mock(AgentRunService.class), mock(ModelGateway.class),
                    mock(ToolRegistry.class), mock(ToolExecutor.class), new ObjectMapper()));

    @Test
    void bindsUnlimitedDefaultAndConfiguredByteLimit() {
        context.run(ctx -> assertThat(ReflectionTestUtils.getField(
                ctx.getBean(ChatOrchestrator.class), "maxAnswerBytes")).isEqualTo(0L));
        context.withPropertyValues("agent.chat.max-answer-bytes=1048576").run(ctx ->
                assertThat(ReflectionTestUtils.getField(ctx.getBean(ChatOrchestrator.class),
                        "maxAnswerBytes")).isEqualTo(1048576L));
    }

    @Test
    void rejectsNegativeLimitsInsteadOfSilentlyDisablingThem() {
        context.withPropertyValues("agent.chat.max-answer-bytes=-1")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
