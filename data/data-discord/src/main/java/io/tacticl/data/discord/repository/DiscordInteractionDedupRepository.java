package io.tacticl.data.discord.repository;

import io.tacticl.data.discord.entity.DiscordInteractionDedup;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface DiscordInteractionDedupRepository extends MongoRepository<DiscordInteractionDedup, String> {

    Optional<DiscordInteractionDedup> findByInteractionId(String interactionId);

    boolean existsByInteractionId(String interactionId);
}
