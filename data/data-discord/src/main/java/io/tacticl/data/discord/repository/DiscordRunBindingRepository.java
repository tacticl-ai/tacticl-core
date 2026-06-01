package io.tacticl.data.discord.repository;

import io.tacticl.data.discord.entity.DiscordRunBinding;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface DiscordRunBindingRepository extends MongoRepository<DiscordRunBinding, String> {

    Optional<DiscordRunBinding> findByPipelineRunIdAndIsActiveTrue(String pipelineRunId);
}
