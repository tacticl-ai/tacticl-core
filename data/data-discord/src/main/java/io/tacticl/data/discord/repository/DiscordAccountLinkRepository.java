package io.tacticl.data.discord.repository;

import io.tacticl.data.discord.entity.DiscordAccountLink;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface DiscordAccountLinkRepository extends MongoRepository<DiscordAccountLink, String> {

    Optional<DiscordAccountLink> findByDiscordUserIdAndIsActiveTrue(String discordUserId);

    // Includes soft-deleted rows so a re-link can re-activate a prior mapping and so a
    // snowflake collision is detected even after an unlink.
    Optional<DiscordAccountLink> findByDiscordUserId(String discordUserId);

    Optional<DiscordAccountLink> findByLinkTokenAndIsActiveTrue(String linkToken);

    Optional<DiscordAccountLink> findByTacticlUserIdAndIsActiveTrue(String tacticlUserId);
}
