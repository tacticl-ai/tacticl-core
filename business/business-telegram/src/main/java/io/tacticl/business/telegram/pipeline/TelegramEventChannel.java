package io.tacticl.business.telegram.pipeline;

import io.tacticl.business.pipeline.channel.PipelineEventChannel;
import io.tacticl.business.pipeline.dto.PipelineCallbackEvent;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.pipeline.entity.PdlcRole;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.repository.PipelineRunRepository;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.repository.SparkRepository;
import io.tacticl.data.telegram.entity.ProjectStatus;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pipeline event sink that streams PDLC events into a project's linked Telegram group.
 *
 * <p>Resolves {@code pipelineRunId → spark → projectId → TelegramProjectLink}, formats
 * the event via {@link TelegramMessageFormatter}, and enqueues on
 * {@link TelegramOutboundQueue}. Events for sparks without a project link, or whose
 * link is not ACTIVE, are silently ignored — keeping cloud/non-group pipelines quiet.
 *
 * <p>Thread routing: uses {@link PipelineCallbackEvent#role()} to look up the forum-topic
 * thread for that role on the link; falls back to the general chat (null thread) when
 * there is no role-specific topic.
 *
 * <p>Destination caching: the three Mongo round-trips (run → spark → link) per event
 * dominate the hot path for busy pipelines. A bounded in-memory cache keyed on
 * pipelineRunId avoids repeated reads of the same stable docs. Archival/status changes
 * are picked up on the next cache miss — the window is bounded by {@link #MAX_CACHE_SIZE}
 * eviction under load; we intentionally accept eventual consistency over coupling the
 * cache to status-change side channels.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramEventChannel implements PipelineEventChannel {

    private static final Logger log = LoggerFactory.getLogger(TelegramEventChannel.class);

    /**
     * Hard cap on cache entries. On overflow we evict a single arbitrary entry per put —
     * plenty simple, plenty fast, and good enough given destinations are stable per run.
     */
    private static final int MAX_CACHE_SIZE = 1000;

    private final PipelineRunRepository runRepo;
    private final SparkRepository sparkRepo;
    private final TelegramProjectLinkRepository linkRepo;
    private final TelegramMessageFormatter formatter;
    private final TelegramOutboundQueue queue;

    /**
     * Present entry = a valid destination resolved for this run.
     * {@link Optional#empty()} entry = resolved to "no destination" (no spark, no project,
     * no active link). Caching both hit and miss outcomes avoids repeating the 3-read
     * dance on every event for cloud/unlinked sparks.
     */
    private final ConcurrentHashMap<String, Optional<ChatDestination>> destinationCache = new ConcurrentHashMap<>();

    public TelegramEventChannel(PipelineRunRepository runRepo,
                                SparkRepository sparkRepo,
                                TelegramProjectLinkRepository linkRepo,
                                TelegramMessageFormatter formatter,
                                TelegramOutboundQueue queue) {
        this.runRepo = runRepo;
        this.sparkRepo = sparkRepo;
        this.linkRepo = linkRepo;
        this.formatter = formatter;
        this.queue = queue;
    }

    @Override
    public void emit(PipelineCallbackEvent event) {
        if (event == null || event.pipelineRunId() == null) {
            return;
        }
        Optional<ChatDestination> destOpt = resolveDestination(event.pipelineRunId(), event.eventType());
        if (destOpt.isEmpty()) {
            return;
        }
        ChatDestination dest = destOpt.get();
        Integer threadId = resolveThreadId(dest.forumTopics(), event.role());

        List<SendMessageRequest> messages = formatter.format(dest.chatId(), threadId, event);
        if (messages.isEmpty()) {
            return;
        }
        for (SendMessageRequest req : messages) {
            boolean accepted = queue.enqueue(dest.chatId(), new OutboundMessage(req));
            if (!accepted) {
                // WHY: queue is bounded; drop-with-log matches existing outbound back-pressure behaviour.
                log.warn("telegram channel: outbound queue full for chat {} — dropped {}",
                         dest.chatId(), event.eventType());
            }
        }
    }

    @Override
    public void complete(String pipelineRunId) {
        // Evict cached destination on terminal events — frees the entry and ensures any
        // later stray events for the same id re-resolve (defensive; callers rarely emit post-complete).
        if (pipelineRunId != null) {
            destinationCache.remove(pipelineRunId);
        }
    }

    private Optional<ChatDestination> resolveDestination(String pipelineRunId, String eventType) {
        Optional<ChatDestination> cached = destinationCache.get(pipelineRunId);
        if (cached != null) {
            return cached;
        }
        Optional<ChatDestination> resolved = loadDestination(pipelineRunId, eventType);
        putWithEviction(pipelineRunId, resolved);
        return resolved;
    }

    private Optional<ChatDestination> loadDestination(String pipelineRunId, String eventType) {
        Optional<PipelineRun> runOpt = runRepo.findById(pipelineRunId);
        if (runOpt.isEmpty()) {
            log.debug("telegram channel: pipelineRun {} not found — skipping event {}", pipelineRunId, eventType);
            return Optional.empty();
        }
        PipelineRun run = runOpt.get();

        Optional<Spark> sparkOpt = sparkRepo.findByIdAndUserId(run.getSparkId(), run.getUserId());
        if (sparkOpt.isEmpty()) {
            log.debug("telegram channel: spark {} not found for run {} — skipping", run.getSparkId(), pipelineRunId);
            return Optional.empty();
        }
        String projectId = sparkOpt.get().getProjectId();
        if (projectId == null || projectId.isBlank()) {
            // Cloud/non-group spark — no Telegram fan-out expected.
            return Optional.empty();
        }

        Optional<TelegramProjectLink> linkOpt = linkRepo.findByProjectIdAndIsActiveTrue(projectId);
        if (linkOpt.isEmpty()) {
            log.debug("telegram channel: no active link for project {} — skipping event {}", projectId, eventType);
            return Optional.empty();
        }
        TelegramProjectLink link = linkOpt.get();
        if (link.getStatus() != ProjectStatus.ACTIVE) {
            log.debug("telegram channel: link {} status={} (not ACTIVE) — skipping", link.getProjectId(), link.getStatus());
            return Optional.empty();
        }

        return Optional.of(new ChatDestination(link.getChatId(), link.getForumTopics()));
    }

    private void putWithEviction(String key, Optional<ChatDestination> value) {
        if (destinationCache.size() >= MAX_CACHE_SIZE) {
            // WHY: bounded cache without the Caffeine dep. Evict a single arbitrary
            // entry per put when full — cheap, good enough for a destination cache
            // whose churn is bounded by the number of concurrent pipeline runs.
            destinationCache.keySet().stream().findAny().ifPresent(destinationCache::remove);
        }
        destinationCache.put(key, value);
    }

    /** Returns the forum-topic thread id for {@code role}, or null when there's no match. */
    private static Integer resolveThreadId(Map<PdlcRole, Long> topics, String roleName) {
        if (topics == null || topics.isEmpty() || roleName == null || roleName.isBlank()) {
            return null;
        }
        PdlcRole role;
        try {
            role = PdlcRole.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            return null;
        }
        Long threadId = topics.get(role);
        // WHY: Telegram API defines message_thread_id as 32-bit; forumTopics stores Long for forward-compat.
        return threadId == null ? null : threadId.intValue();
    }

    /** Cached destination resolved for a pipeline run. */
    private record ChatDestination(long chatId, Map<PdlcRole, Long> forumTopics) {}
}
