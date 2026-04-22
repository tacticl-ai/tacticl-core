package io.tacticl.client.telegram.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class ChatMemberUpdateTest {

    private static final JsonMapper M = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    @Test
    void parsesMyChatMemberBotAdded() {
        String json = """
        {"chat":{"id":-1001,"type":"supergroup","title":"Team"},
         "from":{"id":42,"is_bot":false,"username":"alice"},
         "date":1,
         "old_chat_member":{"status":"left","user":{"id":100,"is_bot":true,"username":"tacticl_bot"}},
         "new_chat_member":{"status":"member","user":{"id":100,"is_bot":true,"username":"tacticl_bot"}}}
        """;
        ChatMemberUpdate u = M.readValue(json, ChatMemberUpdate.class);
        assertEquals(-1001L, u.chat().id());
        assertEquals("member", u.new_chat_member().status());
        assertEquals("tacticl_bot", u.new_chat_member().user().username());
    }
}
