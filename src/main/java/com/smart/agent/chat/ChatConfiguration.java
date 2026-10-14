package com.smart.agent.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.agent.conversation.ConversationService;
import com.smart.agent.knowledge.KnowledgeSearchService;
import com.smart.agent.model.DynamicModelRegistry;
import com.smart.agent.model.ModelGateway;
import com.smart.agent.run.AgentRunService;
import com.smart.agent.tool.ToolExecutor;
import com.smart.agent.tool.ToolRegistry;
import com.smart.agent.routing.QueryIntentResolver;
import com.smart.agent.clarification.PendingClarificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
class ChatConfiguration {
    @Bean
    ChatOrchestrator chatOrchestrator(
            ConversationService conversationService,
            AgentRunService runService,
            ModelGateway modelGateway,
            ObjectProvider<DynamicModelRegistry> modelRegistry,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ObjectMapper objectMapper,
            ObjectProvider<KnowledgeSearchService> knowledgeSearchService,
            @Value("${agent.attachment.public-base-url:}") String attachmentPublicBaseUrl,
            ObjectProvider<com.smart.agent.attachment.AttachmentService> attachmentService,
            com.smart.agent.context.ConversationContextService conversationContextService,
            QueryIntentResolver queryIntentResolver,
            PendingClarificationService pendingClarificationService,
            ObjectProvider<com.smart.agent.question.QuestionRecordService> questionRecords) {
        DynamicModelRegistry registry = modelRegistry.getIfAvailable();
        if (registry == null) {
            ChatOrchestrator orchestrator = new ChatOrchestrator(
                    conversationService, runService, modelGateway, toolRegistry, toolExecutor, objectMapper,
                    knowledgeSearchService.getIfAvailable(), com.smart.agent.chat.ChatOrchestrator.MAX_RUN_DURATION,
                    attachmentService.getIfAvailable(), attachmentPublicBaseUrl, conversationContextService,
                    queryIntentResolver, pendingClarificationService);
            orchestrator.setQuestionRecords(questionRecords.getIfAvailable());
            return orchestrator;
        }
        ChatOrchestrator orchestrator = new ChatOrchestrator(
                conversationService, runService, registry, toolRegistry, toolExecutor, objectMapper,
                knowledgeSearchService.getIfAvailable(), com.smart.agent.chat.ChatOrchestrator.MAX_RUN_DURATION,
                attachmentService.getIfAvailable(), attachmentPublicBaseUrl, conversationContextService,
                queryIntentResolver, pendingClarificationService);
        orchestrator.setQuestionRecords(questionRecords.getIfAvailable());
        return orchestrator;
    }

    @Bean
    ChatSseUseCase chatSseUseCase(ChatOrchestrator orchestrator) {
        return new ChatSseUseCase(orchestrator);
    }
}
