package io.tacticl.business.telegram.outbound;

import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboundDrainerTest {

    @Test
    void drainsOneMessagePerChatPerTick() {
        var queue = new TelegramOutboundQueue(10);
        var bot = mock(TelegramBotClient.class);
        AtomicLong now = new AtomicLong(1_000L);
        Clock clock = Clock.fixed(Instant.ofEpochSecond(now.get()), ZoneOffset.UTC);
        var drainer = new OutboundDrainer(queue, bot, () -> now.get() * 1000L);

        queue.enqueue(42L, new OutboundMessage(SendMessageRequest.plain(42L, "a")));
        queue.enqueue(42L, new OutboundMessage(SendMessageRequest.plain(42L, "b")));

        drainer.drain();                           // sends "a"
        drainer.drain();                           // still within 1s: no send
        now.addAndGet(1);                          // +1 second
        drainer.drain();                           // sends "b"

        verify(bot, times(2)).sendMessage(any());
    }
}
