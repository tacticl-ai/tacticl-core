package io.tacticl.business.discord;

import io.tacticl.client.discord.config.DiscordConfig;
import io.tacticl.data.discord.entity.DiscordInteractionDedup;
import io.tacticl.data.discord.repository.DiscordInteractionDedupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Idempotency guard for inbound Discord interactions. Discord re-delivers an interaction when the
 * original acknowledgement is not received in time, so the controller MUST record the interaction
 * id BEFORE dispatching and reject any duplicate (ACK-and-drop the re-delivery).
 *
 * <p>Backed by a Mongo unique index on {@code interactionId}: the insert is atomic, so two
 * concurrent re-deliveries cannot both win — the loser's insert raises
 * {@link DuplicateKeyException} and we report "already seen". Rows self-expire via the TTL index,
 * so the dedup window stays bounded without manual cleanup. (Discord-side dedup is Mongo-backed
 * rather than the in-memory Caffeine cache Telegram uses because the interactions endpoint is the
 * single entry point and we want dedup to survive a pod restart within the replay window.)
 */
@Service
@ConditionalOnProperty(name = "tacticl.discord.enabled", havingValue = "true")
public class DiscordInteractionDedupService {

    private static final Logger log = LoggerFactory.getLogger(DiscordInteractionDedupService.class);

    private final DiscordInteractionDedupRepository dedupRepo;
    private final DiscordConfig config;

    public DiscordInteractionDedupService(DiscordInteractionDedupRepository dedupRepo,
                                          DiscordConfig config) {
        this.dedupRepo = dedupRepo;
        this.config = config;
    }

    /**
     * Atomically records the interaction id if not already seen.
     *
     * @return {@code true} if this is the first time we've seen the id (caller should dispatch);
     *         {@code false} if it is a re-delivery (caller should ACK-and-drop)
     */
    public boolean markIfFirstSeen(String interactionId, String interactionType) {
        if (interactionId == null || interactionId.isBlank()) {
            // No id to dedup on — treat as first-seen so we never silently drop a real interaction.
            return true;
        }
        try {
            dedupRepo.insert(DiscordInteractionDedup.create(
                interactionId, interactionType, config.getInteractionDedupTtlSeconds()));
            return true;
        } catch (DuplicateKeyException e) {
            log.debug("Dropping duplicate Discord interaction {}", interactionId);
            return false;
        }
    }
}
