package io.tacticl.business.pipeline.ingress;

import io.tacticl.data.pipeline.entity.EntryPoint;
import io.tacticl.data.pipeline.repository.EntryPointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.Set;

/**
 * Idempotent dev seeder for the {@code entry_points} registry. Gated on the dedicated
 * {@code seed-entry-points} profile so it never runs in qa/prod by accident — it is opt-in only.
 *
 * <p>Seeds tacticl-only rows: a Discord test-guild binding pointing at a THROWAWAY test repo
 * (FULL_PDLC), a tacticl WEB channel default, and a Telegram chat binding. Each insert checks for an
 * existing active row on the {@code (channel, externalKey)} key first, so re-running is safe.
 */
@Component
@Profile("seed-entry-points")
public class EntryPointSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EntryPointSeeder.class);

    // Replace with the real test guild id when wiring Discord; placeholder is fine while dormant.
    private static final String DISCORD_TEST_GUILD = "000000000000000000";
    private static final String CHANNEL_DEFAULT_KEY = "__default__";
    private static final String THROWAWAY_REPO = "https://github.com/tacticl-platform/pdlc-ingress-sandbox";
    private static final String SEED_ADMIN = "seed-admin-user";

    private final EntryPointRepository entryPointRepository;

    public EntryPointSeeder(EntryPointRepository entryPointRepository) {
        this.entryPointRepository = entryPointRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed(ChannelType.DISCORD, DISCORD_TEST_GUILD, "tacticl", THROWAWAY_REPO, "FULL_PDLC",
             "tacticl-{userId}", Set.of(SEED_ADMIN), 5.0, "secret/tacticl/github-token", false);

        seed(ChannelType.WEB, CHANNEL_DEFAULT_KEY, "tacticl", THROWAWAY_REPO, "FULL_PDLC",
             "tacticl-{userId}", Set.of(SEED_ADMIN), 5.0, "secret/tacticl/github-token", true);

        seed(ChannelType.TELEGRAM, CHANNEL_DEFAULT_KEY, "tacticl", THROWAWAY_REPO, "FULL_PDLC",
             "tacticl-{userId}", Set.of(SEED_ADMIN), 5.0, "secret/tacticl/github-token", true);
    }

    private void seed(ChannelType channel, String externalKey, String productId, String repoUrl,
                      String playbook, String knowledgeNamespaceTemplate, Set<String> adminUserIds,
                      double costCeilingUsd, String githubTokenRef, boolean isDefaultForChannel) {
        if (entryPointRepository
                .findByChannelAndExternalKeyAndIsActiveTrue(channel.name(), externalKey)
                .isPresent()) {
            log.debug("EntryPoint already present for channel={} key={}; skipping seed",
                      channel, externalKey);
            return;
        }
        EntryPoint ep = EntryPoint.create(channel.name(), externalKey, productId, repoUrl, playbook,
                                          knowledgeNamespaceTemplate, adminUserIds, costCeilingUsd,
                                          githubTokenRef, isDefaultForChannel);
        entryPointRepository.save(ep);
        log.info("Seeded EntryPoint id={} channel={} key={} product={} default={}",
                 ep.getId(), channel, externalKey, productId, isDefaultForChannel);
    }
}
