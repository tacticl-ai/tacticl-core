package io.tacticl.business.voice;

import io.tacticl.business.pipeline.channel.PipelineEventChannel;
import io.tacticl.business.pipeline.dto.PipelineCallbackEvent;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pipeline-event sink that narrates PDLC run updates into the voice session that
 * triggered the run. Implements {@link PipelineEventChannel}; the
 * {@code PipelineEventEmitter} auto-collects this bean and fans every event to it
 * alongside the SSE / Discord / Telegram channels (per-channel try/catch isolates
 * failures — a voice-narration error never blocks the other surfaces).
 *
 * <p>Destination resolution: {@code pipelineRunId → VoiceSession} via the
 * {@link VoiceSessionRegistry} reverse index (written when the triggering turn
 * dispatched an EXPLICIT_TRIGGER). Events for runs without a voice binding
 * (Discord/Telegram/WEB runs) resolve to "no destination" and are ignored.
 *
 * <p>For each recognized event it emits:
 * <ul>
 *   <li>a {@code hud} frame (role / phase / runId / note) to drive the role-strip, and</li>
 *   <li>a spoken narration (assistant {@code transcript} frame + state {@code speaking}
 *       + ElevenLabs TTS audio) so the operator hears progress.</li>
 * </ul>
 * Checkpoint events additionally emit a {@code checkpoint} frame with the
 * APPROVE/CHANGES/REJECT options and register the checkpoint→spark mapping so a
 * later decision frame resolves.
 */
@Component
@ConditionalOnProperty(name = "tacticl.voice.enabled", havingValue = "true")
public class VoiceRunUpdateChannel implements PipelineEventChannel {

    private static final Logger log = LoggerFactory.getLogger(VoiceRunUpdateChannel.class);

    private static final JsonMapper JSON = new JsonMapper();

    private final VoiceSessionRegistry registry;

    public VoiceRunUpdateChannel(VoiceSessionRegistry registry) {
        this.registry = registry;
    }

    /** Identifies this channel for log/telemetry correlation. */
    public String channelType() {
        return "VOICE";
    }

    @Override
    public void emit(PipelineCallbackEvent event) {
        if (event == null || event.pipelineRunId() == null || event.eventType() == null) {
            return;
        }
        Optional<VoiceSession> sessionOpt = registry.byRunId(event.pipelineRunId());
        if (sessionOpt.isEmpty()) {
            // Not a voice-originated run (or its binding was retired) — nothing to narrate.
            return;
        }
        VoiceSession session = sessionOpt.get();
        Narration narration = render(event, session);
        if (narration == null) {
            return;
        }

        VoiceOutbound out = session.outbound();
        // HUD first so the role-strip updates before the voice catches up.
        out.sendControl(VoiceFrames.hud(event.role(), event.phase(), event.pipelineRunId(), narration.speech()));

        if (narration.checkpointId() != null) {
            out.sendControl(VoiceFrames.checkpoint(
                narration.checkpointId(), narration.speech(), VoiceFrames.defaultCheckpointOptions()));
            session.registerCheckpoint(narration.checkpointId(), session.activeSparkId());
        }

        narrate(session, narrationId(event), narration);
    }

    /**
     * Stable transcript id for a narration: one bubble per (run, event type, role).
     * A retried role re-emitting the same {@code ROLE_STARTED} patches that one
     * bubble in place instead of appending a duplicate (the web store merges by id).
     */
    private static String narrationId(PipelineCallbackEvent event) {
        return "run-" + event.pipelineRunId() + "-" + event.eventType()
            + (event.role() == null || event.role().isBlank() ? "" : "-" + event.role());
    }

    @Override
    public void complete(String pipelineRunId) {
        // Soft-retire the run binding on terminal events so stray late events stop resolving.
        registry.retireRun(pipelineRunId);
    }

    /** Speak a line: assistant transcript frame + state speaking + TTS audio. */
    private void narrate(VoiceSession session, String transcriptId, Narration narration) {
        String text = narration.speech();
        if (text == null || text.isBlank()) {
            return;
        }
        // Suppress a line identical to the one just played — a role-retry storm
        // re-emits the same ROLE_STARTED, which would otherwise re-speak and (with a
        // fresh id) stack duplicate bubbles. The deterministic id already coalesces
        // the bubble; this stops the redundant TTS.
        if (!session.markNarration(text)) {
            return;
        }
        session.outbound().sendControl(
            VoiceFrames.transcript("assistant", transcriptId, text, false));
        session.setState(VoiceState.SPEAKING);
        session.outbound().sendControl(VoiceFrames.state(VoiceState.SPEAKING));
        session.tts().speak(text);
        // Record only lifecycle narration into conversation memory so the brain knows
        // what the orb told the operator (e.g. "Pipeline started/completed/failed").
        // Per-role progress chatter is deliberately NOT recorded — it would crowd out
        // the real dialogue within the bounded history.
        if (narration.remember()) {
            session.appendHistory("assistant", text);
        }
    }

    /** Map an event type to a narration line + optional checkpoint id; null = ignore. */
    private Narration render(PipelineCallbackEvent event, VoiceSession session) {
        String role = role(event);
        return switch (event.eventType()) {
            case "PIPELINE_STARTED" -> new Narration("Pipeline started.", null, true);
            case "ROLE_STARTED" -> new Narration(role + " is working" + phaseSuffix(event, role) + ".", null, false);
            case "ROLE_COMPLETED" -> new Narration(role + " finished.", null, false);
            case "ROLE_REWORK" -> new Narration(role + " is reworking.", null, false);
            case "CHECKPOINT_REQUESTED", "CHECKPOINT_PENDING", "REVIEWER_NEEDS_APPROVAL" ->
                new Narration(role + " needs your approval. Say approve, request changes, or reject.",
                    checkpointId(event), true);
            case "PIPELINE_COMPLETED" -> new Narration("Pipeline completed.", null, true);
            case "PIPELINE_FAILED" -> new Narration("Pipeline failed." + reasonSuffix(event), null, true);
            case "PIPELINE_CANCELLED" -> new Narration("Pipeline cancelled.", null, true);
            default -> null;
        };
    }

    private static String role(PipelineCallbackEvent event) {
        return (event.role() == null || event.role().isBlank()) ? "The agent" : event.role();
    }

    /**
     * " on {phase}" — but only when the phase adds information. Upstream sometimes
     * echoes the role into the phase field (e.g. role=PM, phase=PM), which produced
     * the nonsensical "PM is working on PM."; drop the suffix when phase == role.
     */
    private static String phaseSuffix(PipelineCallbackEvent event, String role) {
        String phase = event.phase();
        if (phase == null || phase.isBlank() || phase.equalsIgnoreCase(role)) {
            return "";
        }
        return " on " + phase;
    }

    private String reasonSuffix(PipelineCallbackEvent event) {
        String reason = asString(parse(event.payloadJson()), "reason");
        return (reason == null || reason.isBlank()) ? "" : " " + reason;
    }

    private String checkpointId(PipelineCallbackEvent event) {
        return asString(parse(event.payloadJson()), "checkpointId");
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

    /**
     * A rendered narration: the spoken line, an optional checkpoint id to surface,
     * and whether it should be recorded into conversation memory ({@code remember}
     * — true for pipeline lifecycle + checkpoints, false for per-role chatter).
     */
    private record Narration(String speech, String checkpointId, boolean remember) {
    }
}
