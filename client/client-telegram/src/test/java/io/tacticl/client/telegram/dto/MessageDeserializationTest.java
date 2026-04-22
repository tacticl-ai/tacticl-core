package io.tacticl.client.telegram.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class MessageDeserializationTest {

    private static final JsonMapper M = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    @Test
    void parsesMigrateToChatId() {
        String json = """
            {"message_id":1,"date":1,"chat":{"id":-100,"type":"group"},"migrate_to_chat_id":-1001}
            """;
        Message m = M.readValue(json, Message.class);
        assertEquals(-1001L, m.migrate_to_chat_id());
    }

    @Test
    void parsesVoiceAndThreadId() {
        String json = """
            {"message_id":1,"date":1,"chat":{"id":1,"type":"group"},
             "voice":{"file_id":"abc","duration":3},"message_thread_id":42,"is_topic_message":true}
            """;
        Message m = M.readValue(json, Message.class);
        assertEquals("abc", m.voice().file_id());
        assertEquals(42L, m.message_thread_id());
        assertTrue(m.is_topic_message());
    }

    @Test
    void parsesEntities() {
        String json = """
            {"message_id":1,"date":1,"chat":{"id":1,"type":"group"},"text":"/grant @alice runner",
             "entities":[{"type":"bot_command","offset":0,"length":6}]}
            """;
        Message m = M.readValue(json, Message.class);
        assertEquals(1, m.entities().size());
        assertEquals("bot_command", m.entities().get(0).type());
    }
}
