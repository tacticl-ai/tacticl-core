package io.tacticl.service.voice.token;

import io.tacticl.service.voice.config.VoiceTransportProperties;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Mints and validates short-lived voice session tokens.
 *
 * <p>Why a separate token instead of the access PASETO: the browser cannot put
 * an {@code Authorization: Bearer} header on a {@code new WebSocket(...)} call,
 * so the token has to ride in the {@code ?token=} query string. Putting the
 * long-lived access PASETO there would leak it into proxy/access logs and
 * browser history. So the token endpoint validates the caller's access token via
 * the normal {@code @RequireAuth} path, resolves the userId, then hands back a
 * single-purpose, short-TTL, opaque server-side token bound to that userId. The
 * WS handshake validates only this token.
 *
 * <p>Tokens are opaque 256-bit random strings stored in a bounded in-memory map
 * keyed token → {userId, expiry}. They are single-use friendly but not
 * single-use enforced (a reconnect within TTL reuses the same token). Expired
 * entries are evicted lazily on lookup and opportunistically on mint. This
 * mirrors the bounded-map destination caches in the telegram/discord channels;
 * it does not survive a restart, which is fine — the browser simply re-fetches a
 * token, exactly as it must after the TTL lapses.
 */
@Service
@ConditionalOnProperty(name = "tacticl.voice.enabled", havingValue = "true")
public class VoiceSessionTokenService {

    private static final Logger log = LoggerFactory.getLogger(VoiceSessionTokenService.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();

    private final Duration ttl;

    public VoiceSessionTokenService(VoiceTransportProperties properties) {
        long seconds = properties.getTokenTtlSeconds() > 0 ? properties.getTokenTtlSeconds() : 120;
        this.ttl = Duration.ofSeconds(seconds);
    }

    /**
     * Mint a fresh voice session token bound to {@code userId}.
     *
     * @return an {@link Issued} carrying the opaque token and its TTL (seconds).
     */
    public Issued mint(String userId) {
        evictExpired();
        String token = newToken();
        Instant expiry = Instant.now().plus(ttl);
        tokens.put(token, new Entry(userId, expiry));
        log.debug("Minted voice session token for user={} (ttl={}s)", userId, ttl.toSeconds());
        return new Issued(token, ttl.toSeconds());
    }

    /**
     * Validate a token presented at the WS handshake and resolve its userId.
     * Returns empty for an unknown or expired token.
     */
    public Optional<String> resolveUserId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Entry entry = tokens.get(token);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiry())) {
            tokens.remove(token);
            return Optional.empty();
        }
        return Optional.of(entry.userId());
    }

    /** Invalidate a token (e.g. on WS close), so it can't be replayed within its TTL. */
    public void invalidate(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }

    private void evictExpired() {
        Instant now = Instant.now();
        tokens.entrySet().removeIf(e -> now.isAfter(e.getValue().expiry()));
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private record Entry(String userId, Instant expiry) {
    }

    /** A freshly minted token plus its time-to-live in seconds. */
    public record Issued(String token, long expiresInSeconds) {
    }
}
