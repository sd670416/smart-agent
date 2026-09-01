package com.smart.agent.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
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
    void persistsCreatedConversationAndAppendedMessage() {
        InMemoryConversationRepository repository = new InMemoryConversationRepository();
        ConversationService service = new ConversationService(repository);

        Conversation created = service.create("tenant-1", "user-1", "项目问答");
        Message message = service.appendMessage(created.id(), Message.Role.USER, "项目进度如何");

        assertThat(created.id()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(message.sequence()).isEqualTo(1L);
        assertThat(repository.findById(created.id()).orElseThrow().messages())
                .extracting(Message::content)
                .containsExactly("项目进度如何");
    }

    @Test
    void rejectsAppendingToUnknownConversation() {
        ConversationService service = new ConversationService(new InMemoryConversationRepository());

        assertThatThrownBy(() -> service.appendMessage("missing", Message.Role.USER, "项目进度如何"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class InMemoryConversationRepository implements ConversationRepository {
        private final Map<String, Conversation> conversations = new LinkedHashMap<>();

        @Override
        public Conversation save(Conversation conversation) {
            conversations.put(conversation.id(), conversation);
            return conversation;
        }

        @Override
        public Optional<Conversation> findById(String id) {
            return Optional.ofNullable(conversations.get(id));
        }
    }
}
