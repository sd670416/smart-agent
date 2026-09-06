package com.smart.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.agent.attachment.AttachmentService;
import com.smart.agent.attachment.JpaAttachmentRepository;
import com.smart.agent.conversation.ConversationService;
import com.smart.agent.conversation.JpaConversationRepository;
import com.smart.agent.run.AgentRunService;
import com.smart.agent.run.JpaAgentRunRepository;
import com.smart.agent.tool.ToolExecutor;
import com.smart.agent.tool.ToolRegistry;
import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

class PersistenceConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PersistenceBeans.class);

    @Test
    void disablesPersistenceServicesAndAdaptersWhenPersistenceIsDisabled() {
        contextRunner.withPropertyValues("agent.persistence.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(ConversationService.class);
            assertThat(context).doesNotHaveBean(AgentRunService.class);
            assertThat(context).doesNotHaveBean(AttachmentService.class);
            assertThat(context).doesNotHaveBean(JpaConversationRepository.class);
            assertThat(context).doesNotHaveBean(JpaAgentRunRepository.class);
            assertThat(context).doesNotHaveBean(JpaAttachmentRepository.class);
        });
    }

    @Test
    void enablesPersistenceServicesAndAdaptersWhenPersistenceIsEnabled() {
        contextRunner.withPropertyValues("agent.persistence.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(ConversationService.class);
            assertThat(context).hasSingleBean(AgentRunService.class);
            assertThat(context).hasSingleBean(AttachmentService.class);
            assertThat(context).hasSingleBean(JpaConversationRepository.class);
            assertThat(context).hasSingleBean(JpaAgentRunRepository.class);
            assertThat(context).hasSingleBean(JpaAttachmentRepository.class);
        });
    }

    @Test
    void enablesToolExecutorWithPersistenceEvenWhenItsDefinitionIsRegisteredFirst() {
        contextRunner.withPropertyValues("agent.persistence.enabled=true").run(context ->
                assertThat(context).hasSingleBean(ToolExecutor.class));
    }

    @Test
    void disablesToolExecutorWhenPersistenceIsDisabled() {
        contextRunner.withPropertyValues("agent.persistence.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(ToolExecutor.class));
    }

    @Test
    void localEnvironmentTemplateDeclaresAReplaceableContextSecret() throws Exception {
        String template = Files.readString(Path.of(".env.example"));

        assertThat(template).contains("AGENT_LOCAL_CONTEXT_SECRET=");
        assertThat(template).contains("change-this-local-context-secret-before-sharing");
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackageClasses = ToolExecutor.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {ToolRegistry.class, ToolExecutor.class}))
    @Import({
        ConversationService.class,
        AgentRunService.class,
        AttachmentService.class,
        JpaConversationRepository.class,
        JpaAgentRunRepository.class,
        JpaAttachmentRepository.class
    })
    static class PersistenceBeans {
        @Bean
        EntityManager entityManager() {
            return org.mockito.Mockito.mock(EntityManager.class);
        }
    }
}
