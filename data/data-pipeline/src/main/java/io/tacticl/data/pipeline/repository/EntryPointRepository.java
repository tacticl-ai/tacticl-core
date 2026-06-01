package io.tacticl.data.pipeline.repository;

import io.tacticl.data.pipeline.entity.EntryPoint;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

/**
 * Lookup surface for the {@code entry_points} registry. The resolver probes narrowest-first via
 * {@link #findByChannelAndExternalKeyAndIsActiveTrue} and falls back to the channel default via
 * {@link #findFirstByChannelAndIsDefaultForChannelTrueAndIsActiveTrue}. Channel is stored as the
 * {@code ChannelType} enum name.
 */
public interface EntryPointRepository extends MongoRepository<EntryPoint, String> {

    Optional<EntryPoint> findByChannelAndExternalKeyAndIsActiveTrue(String channel, String externalKey);

    Optional<EntryPoint> findFirstByChannelAndIsDefaultForChannelTrueAndIsActiveTrue(String channel);
}
