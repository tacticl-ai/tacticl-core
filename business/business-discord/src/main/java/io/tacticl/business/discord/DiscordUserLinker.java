package io.tacticl.business.discord;

import io.tacticl.client.discord.config.DiscordConfig;
import io.tacticl.data.discord.entity.DiscordAccountLink;
import io.tacticl.data.discord.repository.DiscordAccountLinkRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Owns the Discord account-link lifecycle — the hard precondition that gates ingress
 * ({@link io.tacticl.business.discord.identity.DiscordIdentityResolver} rejects any unlinked
 * snowflake). Discord identity is keyed on the immutable snowflake, so the flow is snowflake-first
 * (the inverse of Telegram's bot-deep-link):
 *
 * <ol>
 *   <li>In Discord the user runs {@code /link}; the interaction carries their snowflake. We mint a
 *       one-time token and upsert a pending {@link DiscordAccountLink} for that snowflake
 *       ({@link #beginLink}). The token is shown back to the user via an ephemeral followup.</li>
 *   <li>In the authenticated web app the user pastes the token; {@link #redeemToken} binds the
 *       snowflake to their tacticl user id.</li>
 * </ol>
 *
 * <p>Mirrors {@code TelegramUserLinker} (token mint + redeem), collapsed onto the single
 * {@link DiscordAccountLink} entity because the snowflake is the stable key.
 */
@Service
@ConditionalOnProperty(name = "tacticl.discord.enabled", havingValue = "true")
public class DiscordUserLinker {

    private static final Logger log = LoggerFactory.getLogger(DiscordUserLinker.class);

    private final DiscordAccountLinkRepository linkRepo;
    private final DiscordConfig config;
    private final SecureRandom random = new SecureRandom();

    public DiscordUserLinker(DiscordAccountLinkRepository linkRepo, DiscordConfig config) {
        this.linkRepo = linkRepo;
        this.config = config;
    }

    /**
     * Mints a one-time link token for a Discord snowflake and persists it on a pending link row,
     * updating the existing row if one already exists for that snowflake (the unique index forbids
     * a second). Returns the token to be shown to the user (who then redeems it in the web app).
     */
    public String beginLink(String discordUserId, String discordUsername) {
        if (discordUserId == null || discordUserId.isBlank()) {
            throw new IllegalArgumentException("discordUserId is required to begin a link");
        }
        String token = mintToken();
        int ttl = config.getLinkTokenTtlMinutes();

        DiscordAccountLink link = linkRepo.findByDiscordUserId(discordUserId)
            .map(existing -> {
                existing.setDiscordUsername(discordUsername);
                existing.issueToken(token, ttl);
                existing.setActive(true);   // ensure the pending row is redeemable
                return existing;
            })
            .orElseGet(() -> DiscordAccountLink.pending(discordUserId, discordUsername, token, ttl));

        linkRepo.save(link);
        log.info("Issued Discord link token for snowflake {} (ttl={}m)", discordUserId, ttl);
        return token;
    }

    /**
     * Redeems a token against the authenticated tacticl user, binding the snowflake to that account.
     *
     * @return the now-linked record, or empty when the token is unknown/expired
     */
    public Optional<DiscordAccountLink> redeemToken(String token, String tacticlUserId) {
        if (token == null || token.isBlank() || tacticlUserId == null || tacticlUserId.isBlank()) {
            return Optional.empty();
        }
        Optional<DiscordAccountLink> stored = linkRepo.findByLinkTokenAndIsActiveTrue(token);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        DiscordAccountLink link = stored.get();
        if (link.isTokenExpired()) {
            log.info("Discord link token expired for snowflake {}", link.getDiscordUserId());
            return Optional.empty();
        }
        link.redeem(tacticlUserId);
        linkRepo.save(link);
        log.info("Linked Discord snowflake {} to tacticl user {}", link.getDiscordUserId(), tacticlUserId);
        return Optional.of(link);
    }

    /** The Discord account currently linked to this tacticl user, if any. */
    public Optional<DiscordAccountLink> linkedAccount(String tacticlUserId) {
        if (tacticlUserId == null || tacticlUserId.isBlank()) {
            return Optional.empty();
        }
        return linkRepo.findByTacticlUserIdAndIsActiveTrue(tacticlUserId);
    }

    /** Soft-deletes this user's Discord link (a soft-deleted mapping no longer authenticates). */
    public boolean unlink(String tacticlUserId) {
        Optional<DiscordAccountLink> link = linkedAccount(tacticlUserId);
        if (link.isEmpty()) {
            return false;
        }
        DiscordAccountLink l = link.get();
        l.delete();
        linkRepo.save(l);
        log.info("Unlinked Discord snowflake {} from tacticl user {}", l.getDiscordUserId(), tacticlUserId);
        return true;
    }

    private String mintToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
