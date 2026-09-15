package com.smart.agent.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.agent.common.error.AgentException;
import com.smart.agent.model.config.ModelConfig;
import com.smart.agent.model.config.ModelConfigRepository;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ConversationServiceTest {

    @Test
    void appendsMessagesInSequence() {
        Conversation conversation = Conversation.create("tenant-1", "user-1", "项目问答");

        conversation.append(Message.Role.USER, "项目进度如何");
        conversation.append(Message.Role.ASSISTANT, "正在查询");

        assertThat(conversation.messages()).extracting(Message::sequence).containsExactly(1L, 2L);
    }

    @Test
    void persistsCreatedConversationAndAppendedMessageAsDetachedSnapshots() {
        InMemoryConversationRepository repository = new InMemoryConversationRepository();
        ConversationService service = new ConversationService(repository);
        Conversation created = service.create("tenant-1", "user-1", "项目问答");
        created.append(Message.Role.USER, "未保存的消息");

        assertThat(repository.findByIdAndTenantIdAndUserId("tenant-1", "user-1", created.id()).orElseThrow().messages())
                .isEmpty();

        Message message = service.appendMessage("tenant-1", "user-1", created.id(), Message.Role.USER, "项目进度如何");

        assertThat(message.sequence()).isEqualTo(1L);
        assertThat(repository.findByIdAndTenantIdAndUserId("tenant-1", "user-1", created.id()).orElseThrow().messages())
                .extracting(Message::content)
                .containsExactly("项目进度如何");
    }

    @Test
    void rejectsCrossTenantAndCrossUserMessageAppendsWithoutSaving() {
        InMemoryConversationRepository repository = new InMemoryConversationRepository();
        ConversationService service = new ConversationService(repository);
        Conversation conversation = service.create("tenant-1", "user-1", "项目问答");
        int savesBeforeRejectedAppends = repository.saveCount();

        assertThatThrownBy(() -> service.appendMessage("tenant-2", "user-1", conversation.id(), Message.Role.USER, "越权"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.appendMessage("tenant-1", "user-2", conversation.id(), Message.Role.USER, "越权"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(repository.saveCount()).isEqualTo(savesBeforeRejectedAppends);
        assertThat(repository.findByIdAndTenantIdAndUserId("tenant-1", "user-1", conversation.id()).orElseThrow().messages())
                .isEmpty();
    }

    @Test
    void rejectsMissingConversationInTheTrustedScope() {
        ConversationService service = new ConversationService(new InMemoryConversationRepository());

        assertThatThrownBy(() -> service.appendMessage("tenant-1", "user-1", "missing", Message.Role.USER, "项目进度如何"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidConversationAndMessageInputs() {
        assertThatThrownBy(() -> Conversation.create(" ", "user-1", "项目问答")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Conversation.create("tenant-1", null, "项目问答")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Conversation.create("tenant-1", "user-1", " ")).isInstanceOf(IllegalArgumentException.class);

        Conversation conversation = Conversation.create("tenant-1", "user-1", "项目问答");
        assertThatThrownBy(() -> conversation.append(null, "项目进度如何")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> conversation.append(Message.Role.USER, " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newConversationInheritsTheSystemDefaultModel() {
        InMemoryConversationRepository conversations = new InMemoryConversationRepository();
        InMemoryModelConfigRepository models = new InMemoryModelConfigRepository();
        ModelConfig defaultModel = model("model-default", true);
        defaultModel.makeDefault();
        models.save(defaultModel);
        ConversationService service = new ConversationService(conversations, provider(models));

        Conversation created = service.create("tenant-1", "user-1", "项目问答");

        assertThat(created.modelId()).isEqualTo("model-default");
        assertThat(conversations.findByIdAndTenantIdAndUserId("tenant-1", "user-1", created.id())
                .orElseThrow().modelId()).isEqualTo("model-default");
    }

    @Test
    void createsConversationWithoutModelWhenNoDefaultIsConfigured() {
        InMemoryConversationRepository conversations = new InMemoryConversationRepository();
        ConversationService service = new ConversationService(
                conversations, provider(new InMemoryModelConfigRepository()));

        Conversation created = service.create("tenant-1", "user-1", "项目问答");

        assertThat(created.modelId()).isNull();
    }

    @Test
    void switchingModelIsPersistedAndSurvivesReload() {
        InMemoryConversationRepository conversations = new InMemoryConversationRepository();
        InMemoryModelConfigRepository models = new InMemoryModelConfigRepository();
        models.save(model("model-a", true));
        models.save(model("model-b", true));
        ConversationService service = new ConversationService(conversations, provider(models));
        Conversation created = service.create("tenant-1", "user-1", "项目问答", "model-a");

        service.switchModel("tenant-1", "user-1", created.id(), "model-b");

        assertThat(conversations.findByIdAndTenantIdAndUserId("tenant-1", "user-1", created.id())
                .orElseThrow().modelId()).isEqualTo("model-b");
        assertThat(service.find("tenant-1", "user-1", created.id()).modelId()).isEqualTo("model-b");
    }

    @Test
    void rejectsModelSwitchOutsideTheTrustedOwnerScope() {
        InMemoryConversationRepository conversations = new InMemoryConversationRepository();
        InMemoryModelConfigRepository models = new InMemoryModelConfigRepository();
        models.save(model("model-a", true));
        models.save(model("model-b", true));
        ConversationService service = new ConversationService(conversations, provider(models));
        Conversation created = service.create("tenant-1", "user-1", "项目问答", "model-a");
        int savesBeforeRejectedSwitches = conversations.saveCount();

        assertThatThrownBy(() -> service.switchModel("tenant-2", "user-1", created.id(), "model-b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.switchModel("tenant-1", "user-2", created.id(), "model-b"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(conversations.saveCount()).isEqualTo(savesBeforeRejectedSwitches);
        assertThat(service.find("tenant-1", "user-1", created.id()).modelId()).isEqualTo("model-a");
    }

    @Test
    void rejectsSwitchingToADisabledModelWithGuidance() {
        InMemoryConversationRepository conversations = new InMemoryConversationRepository();
        InMemoryModelConfigRepository models = new InMemoryModelConfigRepository();
        models.save(model("model-a", true));
        models.save(model("model-disabled", false));
        ConversationService service = new ConversationService(conversations, provider(models));
        Conversation created = service.create("tenant-1", "user-1", "项目问答", "model-a");
        int savesBeforeRejectedSwitches = conversations.saveCount();

        assertThatThrownBy(() -> service.switchModel("tenant-1", "user-1", created.id(), "model-disabled"))
                .isInstanceOf(AgentException.class)
                .satisfies(error -> assertThat(((AgentException) error).code())
                        .isEqualTo("AGENT_MODEL_DISABLED"));

        assertThat(conversations.saveCount()).isEqualTo(savesBeforeRejectedSwitches);
        assertThat(service.find("tenant-1", "user-1", created.id()).modelId()).isEqualTo("model-a");
    }

    @Test
    void rejectsSwitchingToAnUnknownModel() {
        InMemoryConversationRepository conversations = new InMemoryConversationRepository();
        InMemoryModelConfigRepository models = new InMemoryModelConfigRepository();
        models.save(model("model-a", true));
        ConversationService service = new ConversationService(conversations, provider(models));
        Conversation created = service.create("tenant-1", "user-1", "项目问答", "model-a");

        assertThatThrownBy(() -> service.switchModel("tenant-1", "user-1", created.id(), "missing"))
                .isInstanceOf(AgentException.class)
                .satisfies(error -> assertThat(((AgentException) error).code())
                        .isEqualTo("AGENT_MODEL_NOT_FOUND"));
    }

    private static ModelConfig model(String id, boolean enabled) {
        ModelConfig config = ModelConfig.create("展示名-" + id,
                com.smart.agent.model.config.ModelProviderType.OPENAI_COMPATIBLE,
                com.smart.agent.model.config.ModelDeploymentType.LOCAL,
                "https://api.example.com/v1", "gpt-4o-mini", "sk-test-" + id,
                java.util.Set.of(com.smart.agent.model.config.ModelCapability.TOOL_CALLING),
                Duration.ofSeconds(5), Duration.ofSeconds(30), 0, null);
        setField(config, "id", id);
        if (!enabled) {
            config.setEnabled(false);
        }
        return config;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ModelConfigRepository> provider(ModelConfigRepository repository) {
        return new ObjectProvider<>() {
            @Override
            public ModelConfigRepository getObject(Object... args) {
                return repository;
            }

            @Override
            public ModelConfigRepository getIfAvailable() {
                return repository;
            }

            @Override
            public ModelConfigRepository getIfUnique() {
                return repository;
            }

            @Override
            public ModelConfigRepository getObject() {
                return repository;
            }
        };
    }

    private static final class InMemoryModelConfigRepository implements ModelConfigRepository {
        private final Map<String, ModelConfig> models = new LinkedHashMap<>();

        @Override
        public ModelConfig save(ModelConfig config) {
            models.put(config.id(), config);
            return config;
        }

        @Override
        public Optional<ModelConfig> findById(String id) {
            return Optional.ofNullable(models.get(id));
        }

        @Override
        public Optional<ModelConfig> findDefault() {
            return models.values().stream().filter(ModelConfig::defaultModel).findFirst();
        }

        @Override
        public List<ModelConfig> findEnabled() {
            return models.values().stream().filter(ModelConfig::enabled).toList();
        }

        @Override
        public List<ModelConfig> findAllActive() {
            return new ArrayList<>(models.values());
        }

        @Override
        public List<ModelConfig> findPage(com.smart.agent.model.config.ModelConfigQuery query) {
            return new ArrayList<>(models.values());
        }

        @Override
        public long count(com.smart.agent.model.config.ModelConfigQuery query) {
            return models.size();
        }

        @Override
        public long countReferences(String modelId) {
            return 0L;
        }

        @Override
        public void flush() {
        }
    }

    private static final class InMemoryConversationRepository implements ConversationRepository {
        private final Map<String, ConversationSnapshot> conversations = new LinkedHashMap<>();
        private int saveCount;

        @Override
        public Conversation save(Conversation conversation) {
            conversations.put(conversation.id(), ConversationSnapshot.from(conversation));
            saveCount++;
            return ConversationSnapshot.from(conversation).restore();
        }

        @Override
        public Optional<Conversation> findByIdAndTenantIdAndUserId(String tenantId, String userId, String id) {
            return Optional.ofNullable(conversations.get(id))
                    .filter(snapshot -> snapshot.tenantId().equals(tenantId) && snapshot.userId().equals(userId))
                    .map(ConversationSnapshot::restore);
        }

        int saveCount() {
            return saveCount;
        }
    }

    private record ConversationSnapshot(String id, String tenantId, String userId, String title, String modelId,
                                        List<MessageSnapshot> messages) {
        static ConversationSnapshot from(Conversation conversation) {
            return new ConversationSnapshot(conversation.id(), conversation.tenantId(), conversation.userId(),
                    conversation.title(), conversation.modelId(),
                    conversation.messages().stream().map(MessageSnapshot::from).toList());
        }

        Conversation restore() {
            Conversation conversation = Conversation.create(tenantId, userId, title);
            setField(conversation, "id", id);
            if (modelId != null) {
                setField(conversation, "modelId", modelId);
            }
            for (MessageSnapshot message : messages) {
                conversation.append(message.role(), message.content());
            }
            return conversation;
        }
    }

    private record MessageSnapshot(Message.Role role, String content) {
        static MessageSnapshot from(Message message) {
            return new MessageSnapshot(message.role(), message.content());
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
