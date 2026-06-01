package io.tacticl.business.voice;

import java.util.Map;

/**
 * The outbound half of a voice session — the seam this (transport-neutral)
 * business module uses to push bytes and control frames back to the browser.
 * {@code service-voice} implements this over the live WebSocket; tests implement
 * it with a recording fake.
 *
 * <p>Two channels multiplexed on one socket, mirroring
 * {@code tacticl-web/src/voice/protocol.ts}:
 * <ul>
 *   <li>BINARY DOWN — TTS audio chunks via {@link #sendAudio(byte[])}.</li>
 *   <li>TEXT DOWN — JSON control frames via {@link #sendControl(Map)} (built by
 *       {@link VoiceFrames}).</li>
 * </ul>
 *
 * <p>Implementations must be safe to call from arbitrary threads: STT/TTS client
 * callbacks fire on HttpClient executor threads, and pipeline events fire on the
 * emitter thread.
 */
public interface VoiceOutbound {

    /** Send a BINARY DOWN audio chunk (TTS) to the browser. */
    void sendAudio(byte[] audio);

    /**
     * Send a TEXT DOWN JSON control frame. {@code frame} is a plain map produced
     * by {@link VoiceFrames}; the transport serializes it.
     */
    void sendControl(Map<String, Object> frame);
}
