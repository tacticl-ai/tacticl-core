package io.tacticl.business.pipeline.ingress;

import io.cidadel.framework.exception.CidadelException;
import io.tacticl.data.pipeline.entity.EntryPoint;
import io.tacticl.data.pipeline.repository.EntryPointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the {@link EntryPoint} that governs an inbound ingress request. Lookup-only and
 * narrowest-first: each candidate key is probed in order against the active registry, then the
 * channel-wide default is tried as a last resort. There is NO ask/prompt subsystem — an
 * unresolved request is a hard, typed failure ({@link IngressErrorDetails#ENTRY_POINT_NOT_FOUND}).
 *
 * <p>For Discord the caller supplies candidate keys {@code ["guildId:channelId", "guildId"]}; the
 * resolver tries the channel-scoped binding first, the guild-wide binding next, then the default.
 */
@Service
public class EntryPointResolver {

    private static final Logger log = LoggerFactory.getLogger(EntryPointResolver.class);
    private static final String MODULE_NAME = "business-pipeline";

    private final EntryPointRepository entryPointRepository;

    public EntryPointResolver(EntryPointRepository entryPointRepository) {
        this.entryPointRepository = entryPointRepository;
    }

    /**
     * Resolves from a {@link RunOrigin}, widening the external key narrowest-first before the channel
     * default. A {@code ":"}-delimited key (e.g. Discord {@code "guildId:channelId"}) is probed as the
     * full channel-scoped binding first, then the {@code "guildId"} guild-wide binding, then the
     * channel default. Keys without a {@code ":"} (e.g. a Telegram chatId) are probed as-is.
     */
    public EntryPoint resolve(RunOrigin origin) {
        return resolve(origin.channel(), widen(origin.externalKey()));
    }

    /** Narrowest-first candidates from one external key: the full key, then any {@code ":"} prefix. */
    private static List<String> widen(String externalKey) {
        if (externalKey == null || externalKey.isBlank()) {
            return List.of();
        }
        int sep = externalKey.indexOf(':');
        if (sep > 0) {
            return List.of(externalKey, externalKey.substring(0, sep));
        }
        return List.of(externalKey);
    }

    /**
     * Resolves narrowest-first across {@code candidateKeys} (most specific first), falling back to
     * the channel default. Blank/null candidate keys are skipped.
     *
     * @throws CidadelException {@link IngressErrorDetails#ENTRY_POINT_NOT_FOUND} when nothing matches
     */
    public EntryPoint resolve(ChannelType channel, List<String> candidateKeys) {
        String channelName = channel.name();
        for (String key : candidateKeys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            Optional<EntryPoint> hit =
                entryPointRepository.findByChannelAndExternalKeyAndIsActiveTrue(channelName, key);
            if (hit.isPresent()) {
                log.debug("Resolved EntryPoint {} for channel={} key={}",
                          hit.get().getId(), channelName, key);
                return hit.get();
            }
        }
        Optional<EntryPoint> def =
            entryPointRepository.findFirstByChannelAndIsDefaultForChannelTrueAndIsActiveTrue(channelName);
        if (def.isPresent()) {
            log.debug("Resolved channel-default EntryPoint {} for channel={}",
                      def.get().getId(), channelName);
            return def.get();
        }
        String probed = String.join(",", candidateKeys);
        log.info("No EntryPoint resolved for channel={} candidateKeys=[{}]", channelName, probed);
        throw new CidadelException(IngressErrorDetails.ENTRY_POINT_NOT_FOUND, MODULE_NAME,
                                   channelName, probed);
    }
}
