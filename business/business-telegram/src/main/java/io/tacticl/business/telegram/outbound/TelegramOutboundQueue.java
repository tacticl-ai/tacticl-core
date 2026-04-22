package io.tacticl.business.telegram.outbound;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramOutboundQueue {

    private final int perChatCapacity;
    private final ConcurrentMap<Long, ArrayBlockingQueue<OutboundMessage>> queues = new ConcurrentHashMap<>();

    public TelegramOutboundQueue(
        @Value("${tacticl.telegram.outbound.capacity:200}")
        int perChatCapacity
    ) {
        this.perChatCapacity = perChatCapacity;
    }

    public boolean enqueue(long chatId, OutboundMessage msg) {
        return queues
            .computeIfAbsent(chatId, k -> new ArrayBlockingQueue<>(perChatCapacity))
            .offer(msg);
    }

    public Optional<OutboundMessage> poll(long chatId) {
        var q = queues.get(chatId);
        if (q == null) return Optional.empty();
        return Optional.ofNullable(q.poll());
    }

    public Set<Long> activeChatIds() { return queues.keySet(); }
}
