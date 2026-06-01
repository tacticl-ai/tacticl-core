package io.tacticl.business.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Shape tests for the DOWN control frames (must match tacticl-web/src/voice/protocol.ts). */
class VoiceFramesTest {

    @Test
    void state_carriesWireToken() {
        assertThat(VoiceFrames.state(VoiceState.LISTENING))
            .containsEntry("type", "state")
            .containsEntry("state", "listening");
    }

    @Test
    void level_isClampedToUnitInterval() {
        assertThat(VoiceFrames.level(1.5)).containsEntry("level", 1.0);
        assertThat(VoiceFrames.level(-0.2)).containsEntry("level", 0.0);
        assertThat(VoiceFrames.level(0.4)).containsEntry("level", 0.4);
    }

    @Test
    void transcript_carriesRoleIdAndPartialFlag() {
        Map<String, Object> f = VoiceFrames.transcript("user", "t-1", "hello", true);
        assertThat(f).containsEntry("type", "transcript")
            .containsEntry("role", "user")
            .containsEntry("id", "t-1")
            .containsEntry("text", "hello")
            .containsEntry("partial", true);
    }

    @Test
    void hud_omitsNullFields() {
        Map<String, Object> f = VoiceFrames.hud("Implementer", null, "run-1", null);
        assertThat(f).containsEntry("type", "hud")
            .containsEntry("role", "Implementer")
            .containsEntry("runId", "run-1")
            .doesNotContainKey("phase")
            .doesNotContainKey("note");
    }

    @Test
    void checkpoint_defaultsToProtocolOptionSet() {
        Map<String, Object> f = VoiceFrames.checkpoint("cp-1", "Approve?", null);
        assertThat(f).containsEntry("type", "checkpoint")
            .containsEntry("checkpointId", "cp-1");
        assertThat(f.get("options")).isEqualTo(List.of("APPROVE", "CHANGES", "REJECT"));
    }

    @Test
    void audioFormat_carriesCodecAndOptionalParams() {
        Map<String, Object> f = VoiceFrames.audioFormat("mp3", 44100, 1);
        assertThat(f).containsEntry("type", "audio_format")
            .containsEntry("codec", "mp3")
            .containsEntry("sampleRate", 44100)
            .containsEntry("channels", 1);
    }

    @Test
    void error_fallsBackWhenMessageNull() {
        assertThat(VoiceFrames.error(null)).containsEntry("type", "error").containsEntry("message", "voice error");
    }
}
