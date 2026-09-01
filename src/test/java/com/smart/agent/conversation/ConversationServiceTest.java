package com.smart.agent.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

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

    private record ConversationSnapshot(String id, String tenantId, String userId, String title,
                                        List<MessageSnapshot> messages) {
        static ConversationSnapshot from(Conversation conversation) {
            return new ConversationSnapshot(conversation.id(), conversation.tenantId(), conversation.userId(),
                    conversation.title(), conversation.messages().stream().map(MessageSnapshot::from).toList());
        }

        Conversation restore() {
            Conversation conversation = Conversation.create(tenantId, userId, title);
            setField(conversation, "id", id);
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
