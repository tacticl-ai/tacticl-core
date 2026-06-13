package io.tacticl.business.voice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory for the DOWN (server → browser) JSON control frames defined by the
 * wire protocol in {@code tacticl-web/src/voice/protocol.ts}. Frames are built
 * as plain {@link Map}s so the WS transport layer (service-voice) can serialize
 * them with whatever {@code JsonMapper} it already owns — this module stays free
 * of any transport coupling and the shapes remain trivially unit-testable.
 *
 * <p>Field names here MUST match the protocol's discriminated unions exactly:
 * {@code state | level | transcript | hud | checkpoint | error | audio_format}.
 */
public final class VoiceFrames {

    private VoiceFrames() {
    }

    /** {@code {type:'state', state:'idle'|'listening'|'thinking'|'speaking'}}. */
    public static Map<String, Object> state(VoiceState state) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "state");
        m.put("state", state.wire());
        return m;
    }

    /** {@code {type:'level', level:0..1}} — smoothed amplitude envelope. */
    public static Map<String, Object> level(double level) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "level");
        m.put("level", clamp01(level));
        return m;
    }

    /**
     * {@code {type:'transcript', role, id, text, partial}}. Partials patch the
     * same stable {@code id} until {@code partial:false} arrives.
     */
    public static Map<String, Object> transcript(String role, String id, String text, boolean partial) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "transcript");
        m.put("role", role);
        m.put("id", id);
        m.put("text", text == null ? "" : text);
        m.put("partial", partial);
        return m;
    }

    /**
     * {@code {type:'hud', role?, phase?, runId?, note?}} — PDLC role-strip /
     * active-operation update. Null fields are omitted so the client only sees
     * what changed.
     */
    public static Map<String, Object> hud(String role, String phase, String runId, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "hud");
        if (role != null) {
            m.put("role", role);
        }
        if (phase != null) {
            m.put("phase", phase);
        }
        if (runId != null) {
            m.put("runId", runId);
        }
        if (note != null) {
            m.put("note", note);
        }
        return m;
    }

    /**
     * {@code {type:'checkpoint', checkpointId, title, options:['APPROVE','CHANGES','REJECT']}}.
     * The option tokens are the protocol-facing {@code CheckpointDecision} union — NOT the
     * backend {@code CheckpointDecision} enum (which is APPROVED/REWORK/CANCEL). The decision
     * mapping back happens in {@link VoiceSessionService} when a {@code decision} frame arrives.
     */
    public static Map<String, Object> checkpoint(String checkpointId, String title, List<String> options) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "checkpoint");
        m.put("checkpointId", checkpointId);
        m.put("title", title == null ? "" : title);
        m.put("options", options == null ? defaultCheckpointOptions() : new ArrayList<>(options));
        return m;
    }

    /**
     * {@code {type:'conversation', id, title?}} — sent right after connect so the
     * client learns which durable conversation this session is bound to (the
     * server-assigned id for a new conversation, or the resumed one). The client
     * persists the id to resume on reload and refreshes its conversation picker.
     */
    public static Map<String, Object> conversation(String id, String title) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "conversation");
        m.put("id", id);
        if (title != null) {
            m.put("title", title);
        }
        return m;
    }

    /** {@code {type:'error', message}}. */
    public static Map<String, Object> error(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "error");
        m.put("message", message == null ? "voice error" : message);
        return m;
    }

    /**
     * {@code {type:'audio_format', codec:'mp3'|'pcm16', sampleRate?, channels?}} — sent BEFORE
     * any binary audio when the DOWN codec is not the canonical raw 16 kHz mono PCM16. The client
     * treats the frame's absence as the canonical PCM assumption.
     */
    public static Map<String, Object> audioFormat(String codec, Integer sampleRate, Integer channels) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "audio_format");
        m.put("codec", codec);
        if (sampleRate != null) {
            m.put("sampleRate", sampleRate);
        }
        if (channels != null) {
            m.put("channels", channels);
        }
        return m;
    }

    /** The protocol's full checkpoint option set. */
    public static List<String> defaultCheckpointOptions() {
        return List.of("APPROVE", "CHANGES", "REJECT");
    }

    private static double clamp01(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }
}
