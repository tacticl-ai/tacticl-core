package io.tacticl.business.telegram.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory cache mapping {@code (chatId, @username)} to
 * {@code telegramUserId}, seeded from every inbound message the bot sees.
 *
 * <p>The Telegram Bot API has no server-side username lookup, so commands
 * like {@code /grant @alice contributor} rely on the bot having previously
 * observed {@code @alice} speak in the same group. On cache miss, commands
 * ask the user to "say hi first".
 *
 * <p>V1 has no eviction — entries are tiny and group membership is stable.
 * Revisit if any chat exceeds 10k distinct usernames.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramUsernameCache {

    private final ConcurrentMap<Long, ConcurrentMap<String, Long>> byChat = new ConcurrentHashMap<>();

    /**
     * Record that {@code telegramUserId} is known in {@code chatId} as
     * {@code @username}. No-op if {@code username} is null or blank.
     */
    public void observe(long chatId, long telegramUserId, String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        byChat.computeIfAbsent(chatId, k -> new ConcurrentHashMap<>())
                .put(username.toLowerCase(Locale.ROOT), telegramUserId);
    }

    /**
     * Look up the telegramUserId for {@code @username} in {@code chatId}.
     * Case-insensitive.
     *
     * @return the id, or empty if the username has never been observed here.
     */
    public Optional<Long> lookup(long chatId, String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        ConcurrentMap<String, Long> inner = byChat.get(chatId);
        if (inner == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(inner.get(username.toLowerCase(Locale.ROOT)));
    }
}
