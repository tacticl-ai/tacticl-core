package io.tacticl.business.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.tacticl.business.pipeline.dto.PipelineCallbackEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the run-update → voice-frame mapping. A session is registered in
 * a real registry against a known run id; the channel resolves it and narrates.
 */
class VoiceRunUpdateChannelTest {

    private VoiceSessionRegistry registry;
    private VoiceRunUpdateChannel channel;
    private RecordingOutbound out;
    private ElevenLabsTtsBridge tts;
    private VoiceSession session;

    private static final String RUN_ID = "run-123";

    @BeforeEach
    void setUp() {
        registry = new VoiceSessionRegistry();
        channel = new VoiceRunUpdateChannel(registry);
        out = new RecordingOutbound();
        tts = mock(ElevenLabsTtsBridge.class);
        session = new VoiceSession("sess-1", "user-1", out, mock(DeepgramSttBridge.class), tts);
        session.bindRun(RUN_ID, "spark-9");
        registry.register(session);
        registry.bindRun(RUN_ID, session);
    }

    private static PipelineCallbackEvent event(String type, String role, String phase, String payloadJson) {
        return new PipelineCallbackEvent(RUN_ID, type, role, phase, payloadJson);
    }

    @Test
    void channelType_isVoice() {
        assertThat(channel.channelType()).isEqualTo("VOICE");
    }

    @Test
    void emit_unboundRun_isIgnored() {
        channel.emit(event("PIPELINE_STARTED", null, null, null));
        // Bound run -> something emitted. Now an unbound run id emits nothing.
        RecordingOutbound otherOut = new RecordingOutbound();
        VoiceRunUpdateChannel freshChannel = new VoiceRunUpdateChannel(new VoiceSessionRegistry());
        freshChannel.emit(new PipelineCallbackEvent("nope", "PIPELINE_STARTED", null, null, null));
        assertThat(otherOut.controls).isEmpty();
    }

    @Test
    void emit_roleStarted_emitsHudAndSpeaks() {
        channel.emit(event("ROLE_STARTED", "Implementer", "code", null));

        List<Map<String, Object>> hud = out.framesOfType("hud");
        assertThat(hud).isNotEmpty();
        assertThat(hud.get(0).get("role")).isEqualTo("Implementer");
        assertThat(hud.get(0).get("runId")).isEqualTo(RUN_ID);

        assertThat(out.framesOfType("transcript")).anyMatch(f -> "assistant".equals(f.get("role")));
        assertThat(out.lastState()).contains("speaking");
        verify(tts).speak(anyString());
    }

    @Test
    void emit_roleEchoedAsPhase_doesNotProduceWorkingOnItself() {
        // Upstream sometimes echoes the role into the phase field (role=PM, phase=PM),
        // which used to render the nonsensical "PM is working on PM."
        channel.emit(event("ROLE_STARTED", "PM", "PM", null));

        assertThat(out.framesOfType("transcript"))
            .anyMatch(f -> "PM is working.".equals(f.get("text")));
        assertThat(out.framesOfType("transcript"))
            .noneMatch(f -> String.valueOf(f.get("text")).contains("working on PM"));
    }

    @Test
    void emit_repeatedIdenticalRoleStarted_coalescesAndSpeaksOnce() {
        // A retried role re-emits the same ROLE_STARTED; it must patch one bubble
        // (stable id) and speak only once (consecutive-duplicate suppression).
        channel.emit(event("ROLE_STARTED", "Implementer", "code", null));
        channel.emit(event("ROLE_STARTED", "Implementer", "code", null));
        channel.emit(event("ROLE_STARTED", "Implementer", "code", null));

        List<Map<String, Object>> assistantTranscripts = out.framesOfType("transcript").stream()
            .filter(f -> "assistant".equals(f.get("role")))
            .toList();
        assertThat(assistantTranscripts).hasSize(1);
        verify(tts).speak("Implementer is working on code.");
    }

    @Test
    void emit_checkpoint_emitsCheckpointFrameAndRegistersMapping() {
        channel.emit(event("CHECKPOINT_REQUESTED", "Reviewer", "review",
            "{\"checkpointId\":\"cp-77\"}"));

        List<Map<String, Object>> cps = out.framesOfType("checkpoint");
        assertThat(cps).hasSize(1);
        assertThat(cps.get(0).get("checkpointId")).isEqualTo("cp-77");
        assertThat(cps.get(0).get("options")).isEqualTo(List.of("APPROVE", "CHANGES", "REJECT"));

        // The checkpoint→spark mapping is registered so a later decision resolves.
        assertThat(session.resolveCheckpointSpark("cp-77")).isEqualTo("spark-9");
    }

    @Test
    void emit_pipelineFailed_includesReasonInNarration() {
        channel.emit(event("PIPELINE_FAILED", null, null, "{\"reason\":\"compile error\"}"));

        assertThat(out.framesOfType("transcript"))
            .anyMatch(f -> String.valueOf(f.get("text")).contains("compile error"));
    }

    @Test
    void emit_unknownEventType_isIgnored() {
        channel.emit(event("SOMETHING_ELSE", null, null, null));
        assertThat(out.controls).isEmpty();
    }

    @Test
    void emit_nullOrIncompleteEvent_isIgnored() {
        channel.emit(null);
        channel.emit(new PipelineCallbackEvent(null, "ROLE_STARTED", null, null, null));
        channel.emit(new PipelineCallbackEvent(RUN_ID, null, null, null, null));
        assertThat(out.controls).isEmpty();
    }

    @Test
    void complete_retiresRunBinding() {
        channel.complete(RUN_ID);
        assertThat(registry.byRunId(RUN_ID)).isEmpty();
        // The session itself remains (only the run binding is soft-retired).
        assertThat(registry.bySessionId("sess-1")).isPresent();
    }
}
