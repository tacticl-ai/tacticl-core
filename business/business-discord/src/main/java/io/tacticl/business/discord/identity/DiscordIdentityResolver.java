package io.tacticl.business.discord.identity;

import io.tacticl.data.discord.entity.DiscordAccountLink;
import io.tacticl.data.discord.repository.DiscordAccountLinkRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves a Discord snowflake to the linked tacticl user id. This is the hard account-link
 * precondition for ingress: an unlinked Discord user must never dispatch a PDLC run, so callers
 * treat {@link Optional#empty()} as "reject, do not dispatch" — never as "dispatch anonymously".
 *
 * <p>Mirrors {@code TelegramIdentityResolver}. The {@code isActiveTrue} filter ensures a
 * soft-deleted (unlinked) mapping does not authenticate.
 */
@Service
@ConditionalOnProperty(name = "tacticl.discord.enabled", havingValue = "true")
public class DiscordIdentityResolver {

    private final DiscordAccountLinkRepository linkRepo;

    public DiscordIdentityResolver(DiscordAccountLinkRepository linkRepo) {
        this.linkRepo = linkRepo;
    }

    /**
     * @param discordUserId the invoking Discord user's snowflake
     * @return the linked tacticl user id, or empty when the snowflake is unlinked / soft-deleted
     */
    public Optional<String> resolve(String discordUserId) {
        if (discordUserId == null || discordUserId.isBlank()) {
            return Optional.empty();
        }
        return linkRepo.findByDiscordUserIdAndIsActiveTrue(discordUserId)
            .filter(DiscordAccountLink::isLinked)
            .map(DiscordAccountLink::getTacticlUserId);
    }
}
