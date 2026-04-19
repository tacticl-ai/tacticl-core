package io.tacticl.client.telegram;

import io.cidadel.framework.exception.CidadelException;
import io.github.bucket4j.Bucket;
import io.tacticl.client.telegram.config.TelegramConfig;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelegramBotClientTest {

    @Test
    void sendMessage_rateLimitExceeded_throws() {
        TelegramConfig config = new TelegramConfig();
        config.setBotToken("test-token");
        Bucket bucket = mock(Bucket.class);
        when(bucket.tryConsume(1)).thenReturn(false);

        TelegramBotClient client = new TelegramBotClient(config, bucket);

        assertThrows(CidadelException.class, () ->
            client.sendMessage(SendMessageRequest.plain(123L, "hi"))
        );
    }
}
