package io.tacticl.business.telegram.outbound;

import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboundDrainerTest {

    @Test
    void drainsOneMessagePerChatPerTick() {
        var queue = new TelegramOutboundQueue(10);
        var bot = mock(TelegramBotClient.class);
        AtomicLong now = new AtomicLong(1_000L);
        var drainer = new OutboundDrainer(queue, bot, () -> now.get() * 1000L);

        queue.enqueue(42L, new OutboundMessage(SendMessageRequest.plain(42L, "a")));
        queue.enqueue(42L, new OutboundMessage(SendMessageRequest.plain(42L, "b")));

        drainer.drain();                           // sends "a"
        drainer.drain();                           // still within 1s: no send
        now.addAndGet(1);                          // +1 second
        drainer.drain();                           // sends "b"

        verify(bot, times(2)).sendMessage(any());
    }

    @Test
    void drainNoActiveChatsIsNoOp() {
        var queue = new TelegramOutboundQueue(10);
        var bot = mock(TelegramBotClient.class);
        AtomicLong now = new AtomicLong(1_000L);
        var drainer = new OutboundDrainer(queue, bot, () -> now.get() * 1000L);

        drainer.drain();

        verifyNoInteractions(bot);
    }

    @Test
    void drainContinuesWhenBotThrows() {
        var queue = new TelegramOutboundQueue(10);
        var bot = mock(TelegramBotClient.class);
        AtomicLong now = new AtomicLong(1_000L);
        var drainer = new OutboundDrainer(queue, bot, () -> now.get() * 1000L);

        queue.enqueue(42L, new OutboundMessage(SendMessageRequest.plain(42L, "boom-msg")));
        doThrow(new RuntimeException("boom")).when(bot).sendMessage(any());

        // First drain: send attempted, throws, is swallowed. Message was polled and dropped.
        assertThatCode(drainer::drain).doesNotThrowAnyException();

        // Second drain at same clock: queue is now empty (message was polled), and
        // lastSentMs was NOT updated (put lives inside try, after sendMessage).
        // So the chat is NOT paced, but there is nothing to send either.
        assertThatCode(drainer::drain).doesNotThrowAnyException();

        verify(bot, times(1)).sendMessage(any());
    }

    @Test
    void drainMultiChatSendsOnePerChat() {
        var queue = new TelegramOutboundQueue(10);
        var bot = mock(TelegramBotClient.class);
        AtomicLong now = new AtomicLong(1_000L);
        var drainer = new OutboundDrainer(queue, bot, () -> now.get() * 1000L);

        queue.enqueue(10L, new OutboundMessage(SendMessageRequest.plain(10L, "hello-10")));
        queue.enqueue(20L, new OutboundMessage(SendMessageRequest.plain(20L, "hello-20")));

        drainer.drain();

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(bot, times(2)).sendMessage(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(SendMessageRequest::chat_id)
            .containsExactlyInAnyOrder(10L, 20L);
    }
}
