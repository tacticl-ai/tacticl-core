package io.tacticl.business.voice;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable per-connection state for one voice session. Created by
 * {@link VoiceSessionService#openSession} when the WS connects; retired on
 * disconnect. Holds the outbound sink, the STT/TTS bridges, the currently
 * narrated PDLC run, and the table of open checkpoints (so a spoken/clicked
 * decision can be mapped back to its spark).
 *
 * <p>All cross-thread fields are atomics / concurrent maps: STT callbacks, TTS
 * callbacks, and pipeline-event narration all touch a session from different
 * executor threads.
 */
public class VoiceSession {

    private final String sessionId;

    private final String userId;

    private final VoiceOutbound outbound;

    private final DeepgramSttBridge stt;

    private final ElevenLabsTtsBridge tts;

    /** The PDLC run this session is currently narrating (set on EXPLICIT_TRIGGER dispatch). */
    private final AtomicReference<String> activeRunId = new AtomicReference<>();

    /** The spark backing {@link #activeRunId} (needed to resolve checkpoint decisions). */
    private final AtomicReference<String> activeSparkId = new AtomicReference<>();

    private final AtomicReference<VoiceState> state = new AtomicReference<>(VoiceState.IDLE);

    /** Open checkpoints keyed by checkpointId → backing sparkId, for decision mapping. */
    private final Map<String, String> openCheckpoints = new ConcurrentHashMap<>();

    public VoiceSession(String sessionId,
                        String userId,
                        VoiceOutbound outbound,
                        DeepgramSttBridge stt,
                        ElevenLabsTtsBridge tts) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.outbound = outbound;
        this.stt = stt;
        this.tts = tts;
    }

    public String sessionId() {
        return sessionId;
    }

    public String userId() {
        return userId;
    }

    public VoiceOutbound outbound() {
        return outbound;
    }

    public DeepgramSttBridge stt() {
        return stt;
    }

    public ElevenLabsTtsBridge tts() {
        return tts;
    }

    public String activeRunId() {
        return activeRunId.get();
    }

    public void bindRun(String runId, String sparkId) {
        activeRunId.set(runId);
        activeSparkId.set(sparkId);
    }

    public String activeSparkId() {
        return activeSparkId.get();
    }

    public VoiceState state() {
        return state.get();
    }

    public void setState(VoiceState newState) {
        state.set(newState);
    }

    /** Record an open checkpoint so a later decision frame can resolve its spark. */
    public void registerCheckpoint(String checkpointId, String sparkId) {
        if (checkpointId != null && sparkId != null) {
            openCheckpoints.put(checkpointId, sparkId);
        }
    }

    /** Resolve (and forget) the spark backing a checkpoint; null if unknown. */
    public String resolveCheckpointSpark(String checkpointId) {
        if (checkpointId == null) {
            return null;
        }
        String fromTable = openCheckpoints.remove(checkpointId);
        return fromTable != null ? fromTable : activeSparkId.get();
    }
}
