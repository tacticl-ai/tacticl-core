package io.tacticl.business.discord;

import io.tacticl.business.pipeline.channel.PipelineEventChannel;
import io.tacticl.business.pipeline.dto.PipelineCallbackEvent;
import io.tacticl.client.discord.DiscordRestClient;
import io.tacticl.data.discord.entity.DiscordRunBinding;
import io.tacticl.data.discord.repository.DiscordRunBindingRepository;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.repository.PipelineRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pipeline-event sink that renders PDLC run updates into the Discord channel the run was triggered
 * from. Implements {@link PipelineEventChannel}; {@code PipelineEventEmitter} fans every event to
 * this channel alongside the Telegram/SSE channels (per-channel try/catch isolates failures).
 *
 * <p>Run updates are posted as REAL CHANNEL MESSAGES via the bot token
 * ({@link DiscordRestClient#createChannelMessage}). This is deliberate: the interaction token used
 * for the immediate ACK expires ~15 minutes after the interaction, but a PDLC run can take far
 * longer, so the durable surface must be the bot-token channel message — never the interaction
 * token.
 *
 * <p>Destination resolution: {@code pipelineRunId → DiscordRunBinding → channelId}. The binding is
 * written by the interactions controller at trigger time. Events for runs without a Discord binding
 * (Telegram/WEB/cloud runs) resolve to "no destination" and are silently ignored.
 *
 * <p>Checkpoint events render the Approve / Request changes / Reject buttons whose {@code custom_id}
 * encodes {@code "pdlc:<verb>:<sparkId>:<checkpointId>"} — the exact shape
 * {@link DiscordInboundAdapter} parses back on the button press.
 */
@Component
@ConditionalOnProperty(name = "tacticl.discord.enabled", havingValue = "true")
public class DiscordRunUpdateChannel implements PipelineEventChannel {

    private static final Logger log = LoggerFactory.getLogger(DiscordRunUpdateChannel.class);
    private static final JsonMapper JSON = new JsonMapper();

    /** Discord message-component types / button styles. */
    private static final int COMPONENT_ACTION_ROW = 1;
    private static final int COMPONENT_BUTTON = 2;
    private static final int BUTTON_STYLE_SUCCESS = 3;   // green — Approve
    private static final int BUTTON_STYLE_SECONDARY = 2; // grey  — Request changes
    private static final int BUTTON_STYLE_DANGER = 4;    // red   — Reject

    private final PipelineRunRepository runRepo;
    private final DiscordRunBindingRepository bindingRepo;
    private final DiscordRestClient discord;

    public DiscordRunUpdateChannel(PipelineRunRepository runRepo,
                                   DiscordRunBindingRepository bindingRepo,
                                   DiscordRestClient discord) {
        this.runRepo = runRepo;
        this.bindingRepo = bindingRepo;
        this.discord = discord;
    }

    /** Identifies this channel for log/telemetry correlation. */
    public String channelType() {
        return "DISCORD";
    }

    @Override
    public void emit(PipelineCallbackEvent event) {
        if (event == null || event.pipelineRunId() == null || event.eventType() == null) {
            return;
        }
        Optional<DiscordRunBinding> bindingOpt =
            bindingRepo.findByPipelineRunIdAndIsActiveTrue(event.pipelineRunId());
        if (bindingOpt.isEmpty()) {
            // Not a Discord-originated run (or its binding expired) — nothing to render here.
            return;
        }
        DiscordRunBinding binding = bindingOpt.get();

        Optional<RenderedMessage> rendered = render(event);
        if (rendered.isEmpty()) {
            return;
        }
        try {
            String messageId = discord.createChannelMessage(binding.getChannelId(), rendered.get().payload());
            if (messageId != null) {
                binding.setLastUpdateMessageId(messageId);
                bindingRepo.save(binding);
            }
        } catch (Exception e) {
            // WHY: a Discord API failure for one event must not poison the emitter fan-out or
            // other channels. PipelineEventEmitter already wraps per-channel, but we log here for
            // attribution and swallow so a transient 5xx doesn't escalate.
            log.warn("discord channel: failed to post update for run {} event {}",
                     event.pipelineRunId(), event.eventType(), e);
        }
    }

    @Override
    public void complete(String pipelineRunId) {
        // Soft-retire the binding on terminal events so stray late events stop resolving.
        if (pipelineRunId == null) {
            return;
        }
        bindingRepo.findByPipelineRunIdAndIsActiveTrue(pipelineRunId).ifPresent(b -> {
            b.delete();
            bindingRepo.save(b);
        });
    }

    private Optional<RenderedMessage> render(PipelineCallbackEvent event) {
        return switch (event.eventType()) {
            case "PIPELINE_STARTED" -> Optional.of(text("🚀 Pipeline started"));
            case "ROLE_STARTED" -> Optional.of(text("▶️ " + role(event) + " started"
                + (phaseSuffix(event))));
            case "ROLE_COMPLETED" -> Optional.of(text("✔️ " + role(event) + " finished"));
            case "ROLE_REWORK" -> Optional.of(text("🔁 " + role(event) + " rework requested"));
            case "CHECKPOINT_REQUESTED", "CHECKPOINT_PENDING", "REVIEWER_NEEDS_APPROVAL" ->
                Optional.of(checkpoint(event));
            case "PIPELINE_COMPLETED" -> Optional.of(text("✅ Pipeline completed"));
            case "PIPELINE_FAILED" -> Optional.of(text("❌ Pipeline failed" + reasonSuffix(event)));
            case "PIPELINE_CANCELLED" -> Optional.of(text("🛑 Pipeline cancelled"));
            default -> Optional.empty();
        };
    }

    /** Builds a checkpoint message with the Approve / Changes / Reject button row. */
    private RenderedMessage checkpoint(PipelineCallbackEvent event) {
        JsonNode payload = parse(event.payloadJson());
        String checkpointId = asString(payload, "checkpointId");
        String sparkId = resolveSparkId(event.pipelineRunId());

        String content = "⏸️ Checkpoint: " + role(event) + phaseSuffix(event)
            + "\n\nApprove, request changes, or reject:";

        // Without spark + checkpoint ids we cannot build a resolvable custom_id; degrade to a
        // text-only prompt (resolution can still proceed via the dashboard / REST).
        if (sparkId == null || checkpointId == null || checkpointId.isBlank()) {
            return text(content);
        }

        List<Map<String, Object>> buttons = List.of(
            button(BUTTON_STYLE_SUCCESS, "✅ Approve", customId("approve", sparkId, checkpointId)),
            button(BUTTON_STYLE_SECONDARY, "✏️ Changes", customId("changes", sparkId, checkpointId)),
            button(BUTTON_STYLE_DANGER, "❌ Reject", customId("reject", sparkId, checkpointId))
        );
        Map<String, Object> actionRow = Map.of("type", COMPONENT_ACTION_ROW, "components", buttons);
        return new RenderedMessage(Map.of("content", content, "components", List.of(actionRow)));
    }

    private String resolveSparkId(String pipelineRunId) {
        return runRepo.findById(pipelineRunId).map(PipelineRun::getSparkId).orElse(null);
    }

    private static Map<String, Object> button(int style, String label, String customId) {
        return Map.of(
            "type", COMPONENT_BUTTON,
            "style", style,
            "label", label,
            "custom_id", customId
        );
    }

    private static String customId(String verb, String sparkId, String checkpointId) {
        return "pdlc:" + verb + ":" + sparkId + ":" + checkpointId;
    }

    private static RenderedMessage text(String content) {
        return new RenderedMessage(Map.of("content", content));
    }

    private static String role(PipelineCallbackEvent event) {
        return (event.role() == null || event.role().isBlank()) ? "ROLE" : event.role();
    }

    private static String phaseSuffix(PipelineCallbackEvent event) {
        String phase = event.phase();
        return (phase == null || phase.isBlank()) ? "" : " (" + phase + ")";
    }

    private String reasonSuffix(PipelineCallbackEvent event) {
        String reason = asString(parse(event.payloadJson()), "reason");
        return (reason == null || reason.isBlank()) ? "" : "\n\n" + reason;
    }

    private static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return JSON.nullNode();
        }
        try {
            return JSON.readTree(json);
        } catch (JacksonException e) {
            return JSON.nullNode();
        }
    }

    private static String asString(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asString("");
        return s.isEmpty() ? null : s;
    }

    /** A ready-to-send Discord message payload. */
    private record RenderedMessage(Map<String, Object> payload) {}
}
