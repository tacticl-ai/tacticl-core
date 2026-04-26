package io.tacticl.business.telegram.outbound;

import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class OutboundDrainerTest {

    @Test
    void drainsOneMessagePerChatPerTick() {
        var queue = new TelegramOutboundQueue(10);
        var bot = mock(TelegramBotClient.class);
        var limiter = mock(TelegramRateLimiter.class);
        when(limiter.tryAcquire(42L)).thenReturn(true, false, true);
        var drainer = new OutboundDrainer(queue, bot, limiter);

        queue.enqueue(42L, new OutboundMessage(SendMessageRequest.plain(42L, "a")));
        queue.enqueue(42L, new OutboundMessage(SendMessageRequest.plain(42L, "b")));

        drainer.drain(); // limiter true  → sends "a"
        drainer.drain(); // limiter false → no send (still queued)
        drainer.drain(); // limiter true  → sends "b"

        verify(bot, times(2)).sendMessage(any());
    }

    @Test
    void drainNoActiveChatsIsNoOp() {
        var queue = new TelegramOutboundQueue(10);
        var bot = mock(TelegramBotClient.class);
        var limiter = mock(TelegramRateLimiter.class);
        var drainer = new OutboundDrainer(queue, bot, limiter);

        drainer.drain();

        verifyNoInteractions(bot);
        verifyNoInteractions(limiter);
    }

    @Test
    void drainContinuesWhenBotThrows() {
        var queue = new TelegramOutboundQueue(10);
        var bot = mock(TelegramBotClient.class);
        var limiter = mock(TelegramRateLimiter.class);
        when(limiter.tryAcquire(anyLong())).thenReturn(true);
        var drainer = new OutboundDrainer(queue, bot, limiter);

        queue.enqueue(42L, new OutboundMessage(SendMessageRequest.plain(42L, "boom-msg")));
        doThrow(new RuntimeException("boom")).when(bot).sendMessage(any());

        // First drain: send attempted, throws, swallowed. Message was polled and dropped.
        assertThatCode(drainer::drain).doesNotThrowAnyException();
        // Second drain: queue is empty, nothing to send.
        assertThatCode(drainer::drain).doesNotThrowAnyException();

        verify(bot, times(1)).sendMessage(any());
    }

    @Test
    void drainMultiChatSendsOnePerChat() {
        var queue = new TelegramOutboundQueue(10);
        var bot = mock(TelegramBotClient.class);
        var limiter = mock(TelegramRateLimiter.class);
        when(limiter.tryAcquire(anyLong())).thenReturn(true);
        var drainer = new OutboundDrainer(queue, bot, limiter);

        queue.enqueue(10L, new OutboundMessage(SendMessageRequest.plain(10L, "hello-10")));
        queue.enqueue(20L, new OutboundMessage(SendMessageRequest.plain(20L, "hello-20")));

        drainer.drain();

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(bot, times(2)).sendMessage(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(SendMessageRequest::chat_id)
            .containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    void rateLimiterDeniesSkipMessageStaysQueued() {
        // WHY: a denied tryAcquire must not poll the queue — the message must remain
        // for the next drain tick, otherwise we silently drop traffic just because the
        // limiter said "wait".
        var queue = new TelegramOutboundQueue(10);
        var bot = mock(TelegramBotClient.class);
        var limiter = mock(TelegramRateLimiter.class);
        when(limiter.tryAcquire(42L)).thenReturn(false, true);
        var drainer = new OutboundDrainer(queue, bot, limiter);

        queue.enqueue(42L, new OutboundMessage(SendMessageRequest.plain(42L, "queued")));

        drainer.drain(); // denied — must not poll
        verifyNoInteractions(bot);

        drainer.drain(); // allowed — sends
        verify(bot, times(1)).sendMessage(any());
    }
}
