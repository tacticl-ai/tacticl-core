package io.tacticl.client.telegram;

import io.cidadel.framework.exception.CidadelException;
import io.github.bucket4j.Bucket;
import io.tacticl.client.telegram.config.TelegramConfig;
import io.tacticl.client.telegram.dto.BotCommand;
import io.tacticl.client.telegram.dto.ForumTopic;
import io.tacticl.client.telegram.dto.InlineKeyboardButton;
import io.tacticl.client.telegram.dto.InlineKeyboardMarkup;
import io.tacticl.client.telegram.dto.Message;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TelegramBotClientExtendedTest {

    private static final String BASE_URL = "https://api.telegram.org";
    private static final String TOKEN = "test-token";

    private TelegramConfig config;
    private Bucket bucket;
    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private TelegramBotClient client;

    @BeforeEach
    void setUp() {
        config = new TelegramConfig();
        config.setBotToken(TOKEN);
        config.setBaseUrl(BASE_URL);
        bucket = mock(Bucket.class);
        when(bucket.tryConsume(1)).thenReturn(true);

        builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TelegramBotClient(config, bucket, builder);
    }

    @Test
    void createForumTopicReturnsForumTopic() {
        server.expect(requestTo(BASE_URL + "/bot" + TOKEN + "/createForumTopic"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andRespond(withSuccess(
                "{\"ok\":true,\"result\":{\"message_thread_id\":42,\"name\":\"IMPLEMENTER\",\"icon_color\":7322096}}",
                MediaType.APPLICATION_JSON));

        ForumTopic topic = client.createForumTopic(-1001L, "IMPLEMENTER");

        assertNotNull(topic);
        assertEquals(42L, topic.message_thread_id());
        assertEquals("IMPLEMENTER", topic.name());
        server.verify();
    }

    @Test
    void createForumTopicNonOkResponseThrows() {
        server.expect(requestTo(BASE_URL + "/bot" + TOKEN + "/createForumTopic"))
            .andRespond(withSuccess(
                "{\"ok\":false,\"description\":\"not a forum\"}",
                MediaType.APPLICATION_JSON));

        assertThrows(CidadelException.class, () -> client.createForumTopic(-1001L, "IMPLEMENTER"));
    }

    @Test
    void editMessageTextReturnsUpdatedMessage() {
        server.expect(requestTo(BASE_URL + "/bot" + TOKEN + "/editMessageText"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                "{\"ok\":true,\"result\":{\"message_id\":5,\"date\":1700000000,\"chat\":{\"id\":-1,\"type\":\"group\"},\"text\":\"updated\"}}",
                MediaType.APPLICATION_JSON));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(
            List.of(List.of(new InlineKeyboardButton("Approve", "approve:spark-1"))));
        Message msg = client.editMessageText(-1L, 5L, "updated", markup);

        assertNotNull(msg);
        assertEquals(5L, msg.message_id());
        assertEquals("updated", msg.text());
        server.verify();
    }

    @Test
    void editMessageTextWithoutMarkupSucceeds() {
        server.expect(requestTo(BASE_URL + "/bot" + TOKEN + "/editMessageText"))
            .andRespond(withSuccess(
                "{\"ok\":true,\"result\":{\"message_id\":6,\"date\":1700000000,\"chat\":{\"id\":-2,\"type\":\"group\"},\"text\":\"no markup\"}}",
                MediaType.APPLICATION_JSON));

        Message msg = client.editMessageText(-2L, 6L, "no markup", null);
        assertEquals(6L, msg.message_id());
        server.verify();
    }

    @Test
    void pinChatMessageReturnsTrue() {
        server.expect(requestTo(BASE_URL + "/bot" + TOKEN + "/pinChatMessage"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                "{\"ok\":true,\"result\":true}",
                MediaType.APPLICATION_JSON));

        assertTrue(client.pinChatMessage(-1L, 99L));
        server.verify();
    }

    @Test
    void answerCallbackQuerySucceeds() {
        server.expect(requestTo(BASE_URL + "/bot" + TOKEN + "/answerCallbackQuery"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                "{\"ok\":true,\"result\":true}",
                MediaType.APPLICATION_JSON));

        assertTrue(client.answerCallbackQuery("cb-123", "Approved"));
        server.verify();
    }

    @Test
    void answerCallbackQueryNullTextSucceeds() {
        server.expect(requestTo(BASE_URL + "/bot" + TOKEN + "/answerCallbackQuery"))
            .andRespond(withSuccess(
                "{\"ok\":true,\"result\":true}",
                MediaType.APPLICATION_JSON));

        assertTrue(client.answerCallbackQuery("cb-456", null));
        server.verify();
    }

    @Test
    void leaveChatSucceeds() {
        server.expect(requestTo(BASE_URL + "/bot" + TOKEN + "/leaveChat"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                "{\"ok\":true,\"result\":true}",
                MediaType.APPLICATION_JSON));

        assertTrue(client.leaveChat(-1001L));
        server.verify();
    }

    @Test
    void setWebhookReturnsTrueOnOkResponse() {
        server.expect(requestTo(BASE_URL + "/bot" + TOKEN + "/setWebhook"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                "{\"ok\":true,\"result\":true}",
                MediaType.APPLICATION_JSON));

        assertTrue(client.setWebhook("https://example.com/hook", "secret"));
        server.verify();
    }

    @Test
    void setWebhookNonOkResponseReturnsFalse() {
        server.expect(requestTo(BASE_URL + "/bot" + TOKEN + "/setWebhook"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                "{\"ok\":false,\"description\":\"Bad webhook: HTTPS url must be provided\"}",
                MediaType.APPLICATION_JSON));

        assertFalse(client.setWebhook("http://example.com/hook", "secret"));
        server.verify();
    }

    @Test
    void setWebhookTransportFailureThrows() {
        server.expect(requestTo(BASE_URL + "/bot" + TOKEN + "/setWebhook"))
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                .withServerError());

        assertThrows(CidadelException.class, () ->
            client.setWebhook("https://example.com/hook", "secret"));
    }

    @Test
    void setMyCommandsSucceeds() {
        server.expect(requestTo(BASE_URL + "/bot" + TOKEN + "/setMyCommands"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                "{\"ok\":true,\"result\":true}",
                MediaType.APPLICATION_JSON));

        List<BotCommand> commands = List.of(
            new BotCommand("init", "Initialize project"),
            new BotCommand("status", "Show status"));
        assertTrue(client.setMyCommands(commands, "all_group_chats"));
        server.verify();
    }
}
