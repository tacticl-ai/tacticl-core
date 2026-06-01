package io.tacticl.data.discord.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Idempotency record for a single Discord interaction. Discord re-delivers interactions when
 * the original acknowledgement is not received in time, so the interactions controller MUST
 * record the interaction id BEFORE dispatching to the ingress front door and reject any
 * duplicate. The unique index on {@code interactionId} makes the dedup insert atomic — a
 * concurrent re-delivery fails the insert rather than dispatching twice.
 *
 * <p>Rows self-expire via a TTL index on {@code expiresAt}; Discord never replays an
 * interaction beyond a few minutes, so a short retention window is sufficient.
 */
@Document("discord_interaction_dedup")
public class DiscordInteractionDedup extends BaseMongoEntity {

    @Indexed(unique = true)
    private String interactionId;

    private String interactionType;

    private Instant seenAt;

    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    public static DiscordInteractionDedup create(String interactionId, String interactionType, int ttlSeconds) {
        var dedup = new DiscordInteractionDedup();
        dedup.interactionId = interactionId;
        dedup.interactionType = interactionType;
        dedup.seenAt = Instant.now();
        dedup.expiresAt = dedup.seenAt.plusSeconds(ttlSeconds);
        return dedup;
    }

    public String getInteractionId() { return interactionId; }
    public void setInteractionId(String interactionId) { this.interactionId = interactionId; }

    public String getInteractionType() { return interactionType; }
    public void setInteractionType(String interactionType) { this.interactionType = interactionType; }

    public Instant getSeenAt() { return seenAt; }
    public void setSeenAt(Instant seenAt) { this.seenAt = seenAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
