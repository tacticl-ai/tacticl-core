package io.tacticl.client.elevenlabs.client;

import io.tacticl.client.elevenlabs.dto.AudioChunk;
import java.util.function.Consumer;

/**
 * One streaming TTS session against ElevenLabs.
 *
 * <p>Lifecycle: open via
 * {@link ElevenLabsClient#open(ElevenLabsSessionConfig)} → register handlers →
 * push text chunks → call {@link #close()} (or {@link #flush()} at tool-use
 * barriers per SAD §5.10.5).
 *
 * <p>Sessions are short-lived: SAD §5.3 documents one bridge per utterance and
 * §5.10.5 explicitly allows multiple WS connections per assistant turn because
 * the Turbo handshake is sub-100ms.
 */
public interface ElevenLabsSession {

    /**
     * Forward a text chunk into the streaming WS. Inbound audio is appended as
     * ElevenLabs synthesizes the corresponding speech.
     */
    void sendTextChunk(String text);

    /**
     * Flush ElevenLabs' internal buffer so any in-progress sentence finishes
     * audibly. Required at tool-use barriers (SAD §5.10.5) — does not close
     * the session.
     */
    void flush();

    /** Register the handler invoked for every {@link AudioChunk} returned. */
    void onAudioChunk(Consumer<AudioChunk> handler);

    /** Register the handler invoked exactly once after the final audio chunk. */
    void onDone(Runnable handler);

    /** Register the handler invoked for any error frame or transport failure. */
    void onError(Consumer<Throwable> handler);

    /** Whether the underlying WebSocket is currently open. */
    boolean isOpen();

    /**
     * Close the session. Safe to call mid-stream as a barge-in primitive —
     * SAD §5.10.5 requires that {@code close()} return without waiting for
     * in-flight audio to finish flushing.
     */
    void close();

}
