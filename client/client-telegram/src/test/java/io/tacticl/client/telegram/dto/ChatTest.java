package io.tacticl.client.telegram.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ChatTest {

    private static final JsonMapper M = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    @Test
    void parsesIsForumTrue() {
        String json = """
            {"id":-100,"type":"supergroup","title":"Proj","is_forum":true}
            """;
        Chat chat = M.readValue(json, Chat.class);
        assertThat(chat.is_forum()).isTrue();
        assertThat(chat.type()).isEqualTo("supergroup");
    }

    @Test
    void parsesIsForumFalse() {
        String json = """
            {"id":-100,"type":"supergroup","title":"Proj","is_forum":false}
            """;
        Chat chat = M.readValue(json, Chat.class);
        assertThat(chat.is_forum()).isFalse();
    }

    @Test
    void isForumNullWhenMissingTreatedAsFalse() {
        String json = """
            {"id":-100,"type":"group","title":"Proj"}
            """;
        Chat chat = M.readValue(json, Chat.class);
        // Telegram omits is_forum for non-forum chats — callers treat null == false
        assertThat(chat.is_forum()).isNull();
        assertThat(Boolean.TRUE.equals(chat.is_forum())).isFalse();
    }
}
