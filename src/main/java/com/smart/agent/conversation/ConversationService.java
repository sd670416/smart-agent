package com.smart.agent.conversation;

import com.smart.agent.common.error.AgentException;
import com.smart.agent.model.config.ModelConfig;
import com.smart.agent.model.config.ModelConfigRepository;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ObjectProvider<ModelConfigRepository> modelConfigRepositoryProvider;

    public ConversationService(ConversationRepository conversationRepository) {
        this(conversationRepository, (ObjectProvider<ModelConfigRepository>) null);
    }

    @Autowired
    public ConversationService(ConversationRepository conversationRepository,
            ObjectProvider<ModelConfigRepository> modelConfigRepositoryProvider) {
        this.conversationRepository = conversationRepository;
        this.modelConfigRepositoryProvider = modelConfigRepositoryProvider;
    }

    @Transactional
    public Conversation create(String tenantId, String userId, String title) {
        return create(tenantId, userId, title, null);
    }

    /**
     * 新建会话。未指定模型时继承系统默认模型，保证会话在首次发送前就有确定的模型归属。
     */
    @Transactional
    public Conversation create(String tenantId, String userId, String title, String modelId) {
        String resolved = resolveModelIdForCreate(modelId);
        Conversation conversation = resolved == null
                ? Conversation.create(tenantId, userId, title)
                : Conversation.create(tenantId, userId, title, resolved);
        return conversationRepository.save(conversation);
    }

    @Transactional
    public Message appendMessage(String tenantId, String userId, String conversationId, Message.Role role, String content) {
        return appendMessage(tenantId, userId, conversationId, role, content, null);
    }

    @Transactional
    public Message appendMessage(String tenantId, String userId, String conversationId, Message.Role role, String content,
            String attachmentsJson) {
        Conversation conversation = requireConversation(tenantId, userId, conversationId);
        Message message = conversation.append(role, content, attachmentsJson);
        conversationRepository.save(conversation);
        return message;
    }

    @Transactional(readOnly = true)
    public Conversation find(String tenantId, String userId, String conversationId) {
        return requireConversation(tenantId, userId, conversationId);
    }

    @Transactional
    public Conversation rename(String tenantId, String userId, String conversationId, String title) {
        Conversation conversation = requireConversation(tenantId, userId, conversationId);
        conversation.rename(requireText(title, "title"));
        return conversationRepository.save(conversation);
    }

    /**
     * 切换会话使用的模型。只能操作属于当前租户和当前用户的会话，
     * 且目标模型必须存在并处于启用状态。
     */
    @Transactional
    public Conversation switchModel(String tenantId, String userId, String conversationId, String modelId) {
        Conversation conversation = requireConversation(tenantId, userId, conversationId);
        String target = requireText(modelId, "modelId");
        requireSwitchableModel(target);
        conversation.assignModel(target);
        return conversationRepository.save(conversation);
    }

    @Transactional
    public void delete(String tenantId, String userId, String conversationId) {
        Conversation conversation = requireConversation(tenantId, userId, conversationId);
        conversationRepository.delete(conversation);
    }

    @Transactional(readOnly = true)
    public List<Conversation> list(String tenantId, String userId) {
        return conversationRepository.findByTenantIdAndUserId(tenantId, userId);
    }

    private Conversation requireConversation(String tenantId, String userId, String conversationId) {
        return conversationRepository.findByIdAndTenantIdAndUserId(
                        requireText(tenantId, "tenantId"), requireText(userId, "userId"), requireText(conversationId, "conversationId"))
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));
    }

    private String resolveModelIdForCreate(String modelId) {
        if (modelId != null && !modelId.isBlank()) {
            requireSwitchableModel(modelId);
            return modelId;
        }
        ModelConfigRepository repository = repositoryOrNull();
        if (repository == null) {
            return null;
        }
        return repository.findDefault().map(ModelConfig::id).orElse(null);
    }

    /**
     * 目标模型必须存在且已启用。错误码用于前端给出可操作的中文引导，
     * 不允许静默回退到无权限或未知模型。
     */
    private void requireSwitchableModel(String modelId) {
        ModelConfigRepository repository = repositoryOrNull();
        if (repository == null) {
            return;
        }
        ModelConfig config = repository.findById(modelId).orElse(null);
        if (config == null) {
            throw new AgentException("AGENT_MODEL_NOT_FOUND", org.springframework.http.HttpStatus.NOT_FOUND,
                    "Model config not found: " + modelId);
        }
        if (!config.enabled()) {
            throw new AgentException("AGENT_MODEL_DISABLED", org.springframework.http.HttpStatus.CONFLICT,
                    "Model config is disabled: " + modelId);
        }
    }

    private ModelConfigRepository repositoryOrNull() {
        return modelConfigRepositoryProvider == null ? null : modelConfigRepositoryProvider.getIfAvailable();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
