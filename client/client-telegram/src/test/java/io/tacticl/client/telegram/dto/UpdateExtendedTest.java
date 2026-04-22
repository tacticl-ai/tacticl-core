package io.tacticl.client.telegram.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class UpdateExtendedTest {

    private static final JsonMapper M = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    @Test
    void parsesMyChatMemberInUpdate() {
        String json = """
        {"update_id":1,
         "my_chat_member":{"chat":{"id":-1,"type":"group"},
                           "from":{"id":42,"is_bot":false,"username":"alice"},
                           "date":1,
                           "old_chat_member":{"status":"left","user":{"id":100,"is_bot":true}},
                           "new_chat_member":{"status":"member","user":{"id":100,"is_bot":true}}}}
        """;
        Update u = M.readValue(json, Update.class);
        assertNotNull(u.my_chat_member());
        assertEquals("member", u.my_chat_member().new_chat_member().status());
    }

    @Test
    void parsesCallbackQuery() {
        String json = """
        {"update_id":2,
         "callback_query":{"id":"cb-1","from":{"id":42,"is_bot":false},
                           "data":"approve:spark-1:cp-1",
                           "message":{"message_id":10,"date":1,"chat":{"id":-1,"type":"group"}}}}
        """;
        Update u = M.readValue(json, Update.class);
        assertEquals("cb-1", u.callback_query().id());
        assertEquals("approve:spark-1:cp-1", u.callback_query().data());
    }
}
