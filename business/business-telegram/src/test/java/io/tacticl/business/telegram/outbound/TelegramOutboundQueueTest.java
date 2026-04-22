package io.tacticl.business.telegram.outbound;

import io.tacticl.client.telegram.dto.SendMessageRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TelegramOutboundQueueTest {

    @Test
    void enqueueAndPollFifo() {
        var q = new TelegramOutboundQueue(100);
        q.enqueue(1L, new OutboundMessage(SendMessageRequest.plain(1L, "a")));
        q.enqueue(1L, new OutboundMessage(SendMessageRequest.plain(1L, "b")));

        assertEquals("a", q.poll(1L).orElseThrow().request().text());
        assertEquals("b", q.poll(1L).orElseThrow().request().text());
        assertTrue(q.poll(1L).isEmpty());
    }

    @Test
    void capacityBoundedPerChat() {
        var q = new TelegramOutboundQueue(2);
        q.enqueue(1L, new OutboundMessage(SendMessageRequest.plain(1L, "a")));
        q.enqueue(1L, new OutboundMessage(SendMessageRequest.plain(1L, "b")));
        assertFalse(q.enqueue(1L, new OutboundMessage(SendMessageRequest.plain(1L, "c"))));
    }

    @Test
    void snapshotKeysReturnsAllActiveChats() {
        var q = new TelegramOutboundQueue(10);
        q.enqueue(1L, new OutboundMessage(SendMessageRequest.plain(1L, "a")));
        q.enqueue(2L, new OutboundMessage(SendMessageRequest.plain(2L, "b")));
        assertTrue(q.activeChatIds().containsAll(java.util.List.of(1L, 2L)));
    }
}
