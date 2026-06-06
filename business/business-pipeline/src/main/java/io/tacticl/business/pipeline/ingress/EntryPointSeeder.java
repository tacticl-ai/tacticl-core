package io.tacticl.business.pipeline.ingress;

import io.tacticl.business.pipeline.ingress.IngressRegistryProperties.EntryPointDef;
import io.tacticl.data.pipeline.entity.EntryPoint;
import io.tacticl.data.pipeline.repository.EntryPointRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Idempotent, config-driven seeder for the {@code entry_points} registry. Runs on every boot and
 * upserts whatever rows are declared under {@code tacticl.ingress.entry-points[]}
 * ({@link IngressRegistryProperties}) — config is the source of truth for the registry, so adding a
 * Discord alert channel that routes to {@code BUG_FIX}, or the VOICE surface, is a config change
 * rather than a code change.
 *
 * <p>Each row is matched on its {@code (channel, externalKey)} key: an existing active row has its
 * mutable fields refreshed; a missing row is inserted. With no configured rows it no-ops, so the
 * registry stays empty (and every state-changing dispatch correctly fails {@code ENTRY_POINT_NOT_FOUND})
 * until an environment declares its entry points.
 */
@Component
public class EntryPointSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EntryPointSeeder.class);

    private final EntryPointRepository entryPointRepository;
    private final IngressRegistryProperties properties;

    public EntryPointSeeder(EntryPointRepository entryPointRepository,
                            IngressRegistryProperties properties) {
        this.entryPointRepository = entryPointRepository;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        var defs = properties.getEntryPoints();
        if (defs == null || defs.isEmpty()) {
            log.debug("No tacticl.ingress.entry-points configured; registry seeding skipped");
            return;
        }
        int upserted = 0;
        for (EntryPointDef def : defs) {
            if (!isValid(def)) {
                continue;
            }
            upsert(def);
            upserted++;
        }
        log.info("EntryPoint registry seed complete: {} of {} configured rows upserted",
                 upserted, defs.size());
    }

    private boolean isValid(EntryPointDef def) {
        if (def.getChannel() == null || def.getChannel().isBlank()
            || def.getExternalKey() == null || def.getExternalKey().isBlank()) {
            log.warn("Skipping entry-point with blank channel/externalKey: {}", def.getExternalKey());
            return false;
        }
        try {
            ChannelType.valueOf(def.getChannel().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Skipping entry-point with unknown channel '{}' (key={})",
                     def.getChannel(), def.getExternalKey());
            return false;
        }
        return true;
    }

    private void upsert(EntryPointDef def) {
        String channel = ChannelType.valueOf(def.getChannel().trim().toUpperCase()).name();
        Optional<EntryPoint> existing =
            entryPointRepository.findByChannelAndExternalKeyAndIsActiveTrue(channel, def.getExternalKey());

        if (existing.isPresent()) {
            EntryPoint ep = existing.get();
            ep.setProductId(def.getProductId());
            ep.setRepoUrl(def.getRepoUrl());
            ep.setDefaultPlaybook(def.getPlaybook());
            ep.setKnowledgeNamespaceTemplate(def.getKnowledgeNamespaceTemplate());
            ep.setAdminUserIds(new HashSet<>(def.getAdminUserIds()));
            ep.setCostCeilingUsd(def.getCostCeilingUsd());
            ep.setGithubTokenRef(def.getGithubTokenRef());
            ep.setDefaultForChannel(def.isDefaultForChannel());
            ep.setUpdatedAt(Instant.now());
            entryPointRepository.save(ep);
            log.info("Refreshed EntryPoint channel={} key={} product={} playbook={} default={}",
                     channel, def.getExternalKey(), def.getProductId(), def.getPlaybook(),
                     def.isDefaultForChannel());
            return;
        }

        EntryPoint ep = EntryPoint.create(channel, def.getExternalKey(), def.getProductId(),
            def.getRepoUrl(), def.getPlaybook(), def.getKnowledgeNamespaceTemplate(),
            new HashSet<>(def.getAdminUserIds()), def.getCostCeilingUsd(),
            def.getGithubTokenRef(), def.isDefaultForChannel());
        entryPointRepository.save(ep);
        log.info("Seeded EntryPoint id={} channel={} key={} product={} playbook={} default={}",
                 ep.getId(), channel, def.getExternalKey(), def.getProductId(), def.getPlaybook(),
                 def.isDefaultForChannel());
    }
}
