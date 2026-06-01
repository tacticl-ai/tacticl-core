package io.tacticl.business.voice;

/**
 * Sphere / HUD state mirror of the wire protocol's {@code VoiceWireState}
 * (see {@code tacticl-web/src/voice/protocol.ts}). Emitted to the browser as a
 * {@code {type:'state', state:...}} control frame so the client can drive the
 * orb animation.
 *
 * <p>The lifecycle for a turn is:
 * {@link #IDLE} → {@link #LISTENING} (mic open / VAD start) →
 * {@link #THINKING} (final transcript dispatched into ingress) →
 * {@link #SPEAKING} (TTS narration playing) → {@link #IDLE}.
 */
public enum VoiceState {
    IDLE("idle"),
    LISTENING("listening"),
    THINKING("thinking"),
    SPEAKING("speaking");

    private final String wire;

    VoiceState(String wire) {
        this.wire = wire;
    }

    /** Lower-case token sent on the wire (matches the TS {@code VoiceWireState} union). */
    public String wire() {
        return wire;
    }
}
