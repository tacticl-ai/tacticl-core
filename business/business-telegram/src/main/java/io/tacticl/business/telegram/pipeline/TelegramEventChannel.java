package io.tacticl.business.telegram.pipeline;

import io.tacticl.business.pipeline.channel.PipelineEventChannel;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pipeline event sink that streams PDLC events into a project's linked Telegram group.
 *
 * <p>Resolves {@code pipelineRunId → spark → projectId → TelegramProjectLink}, formats
 * the event via {@link TelegramMessageFormatter}, and enqueues on
 * {@link TelegramOutboundQueue}. Events for sparks without a project link, or whose
 * link is not ACTIVE, are silently ignored — keeping cloud/non-group pipelines quiet.
 *
 * <p>Thread routing: if the link defines a forum-topic thread for the event's role
 * (extracted from payload), the message targets that thread; otherwise it goes to
 * the general chat.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramEventChannel implements PipelineEventChannel {

    private static final Logger log = LoggerFactory.getLogger(TelegramEventChannel.class);
    private static final JsonMapper JSON = new JsonMapper();

    private final PipelineRunRepository runRepo;
    private final SparkRepository sparkRepo;
    private final TelegramProjectLinkRepository linkRepo;
    private final TelegramMessageFormatter formatter;
    private final TelegramOutboundQueue queue;

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
    public void emit(String pipelineRunId, String eventName, Object payload) {
        Optional<PipelineRun> runOpt = runRepo.findById(pipelineRunId);
        if (runOpt.isEmpty()) {
            log.debug("telegram channel: pipelineRun {} not found — skipping event {}", pipelineRunId, eventName);
            return;
        }
        PipelineRun run = runOpt.get();

        Optional<Spark> sparkOpt = sparkRepo.findByIdAndUserId(run.getSparkId(), run.getUserId());
        if (sparkOpt.isEmpty()) {
            log.debug("telegram channel: spark {} not found for run {} — skipping", run.getSparkId(), pipelineRunId);
            return;
        }
        String projectId = sparkOpt.get().getProjectId();
        if (projectId == null || projectId.isBlank()) {
            // Cloud/non-group spark — no Telegram fan-out expected.
            return;
        }

        Optional<TelegramProjectLink> linkOpt = linkRepo.findByProjectIdAndIsActiveTrue(projectId);
        if (linkOpt.isEmpty()) {
            log.debug("telegram channel: no active link for project {} — skipping event {}", projectId, eventName);
            return;
        }
        TelegramProjectLink link = linkOpt.get();
        if (link.getStatus() != ProjectStatus.ACTIVE) {
            log.debug("telegram channel: link {} status={} (not ACTIVE) — skipping", link.getProjectId(), link.getStatus());
            return;
        }

        Integer threadId = resolveThreadId(link, payload);

        List<SendMessageRequest> messages = formatter.format(link.getChatId(), threadId, eventName, payload);
        if (messages.isEmpty()) {
            return;
        }
        for (SendMessageRequest req : messages) {
            boolean accepted = queue.enqueue(link.getChatId(), new OutboundMessage(req));
            if (!accepted) {
                // WHY: queue is bounded; drop-with-log matches existing outbound back-pressure behaviour.
                log.warn("telegram channel: outbound queue full for chat {} — dropped {}", link.getChatId(), eventName);
            }
        }
    }

    /**
     * Looks up the forum-topic thread id for the role referenced in {@code payload}.
     * Returns {@code null} when the link has no per-role topic map, the payload has no
     * role, or the role is not present in the map (fall back to the general chat).
     */
    private Integer resolveThreadId(TelegramProjectLink link, Object payload) {
        Map<PdlcRole, Long> topics = link.getForumTopics();
        if (topics == null || topics.isEmpty()) {
            return null;
        }
        return extractRole(payload)
            .map(topics::get)
            // WHY: Telegram API defines message_thread_id as 32-bit; forumTopics stores Long for forward-compat.
            .map(Long::intValue)
            .orElse(null);
    }

    private static Optional<PdlcRole> extractRole(Object payload) {
        if (payload == null) {
            return Optional.empty();
        }
        String roleStr = readRoleField(payload);
        if (roleStr == null || roleStr.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(PdlcRole.valueOf(roleStr));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String readRoleField(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Object v = map.get("role");
            return v == null ? null : v.toString();
        }
        if (payload instanceof JsonNode node) {
            JsonNode v = node.path("role");
            return v.isMissingNode() || v.isNull() ? null : v.asString("");
        }
        // WHY: some emitters pass POJOs/records. Use Jackson to normalise before reading "role".
        try {
            JsonNode node = JSON.valueToTree(payload);
            JsonNode v = node.path("role");
            return v.isMissingNode() || v.isNull() ? null : v.asString("");
        } catch (Exception e) {
            return null;
        }
    }
}
