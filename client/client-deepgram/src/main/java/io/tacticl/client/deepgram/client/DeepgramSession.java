package io.tacticl.client.deepgram.client;

import io.tacticl.client.deepgram.dto.DeepgramFinalTranscript;
import io.tacticl.client.deepgram.dto.DeepgramPartialTranscript;
import java.util.function.Consumer;

/**
 * Streaming STT session against Deepgram.
 *
 * <p>Lifecycle: open via {@link DeepgramClient#open}, register handlers,
 * push 16kHz s16le PCM via {@link #sendAudio}, then {@link #close} on
 * session/voice-mode end. The caller (DeepgramStreamBridge in business-voice)
 * owns reconnect behaviour — this interface intentionally exposes no reconnect.
 */
public interface DeepgramSession {

    /**
     * Forward a 16kHz mono s16le PCM audio chunk to Deepgram as a binary frame.
     * Recommended chunk size: 20ms (~640 bytes). Larger chunks work too.
     */
    void sendAudio(byte[] pcmChunk);

    /** Handler for interim results ({@code is_final: false}). */
    void onPartialTranscript(Consumer<DeepgramPartialTranscript> handler);

    /**
     * Handler for finalized utterances ({@code is_final: true && speech_final: true}).
     * Per SAD §5.2 this is the trigger for {@code onUserTranscript} workflow signals.
     */
    void onFinalTranscript(Consumer<DeepgramFinalTranscript> handler);

    /** VAD start-of-speech ({@code "type": "SpeechStarted"}) — drives sphere listening animation. */
    void onSpeechStarted(Runnable handler);

    /** VAD end-of-utterance ({@code "type": "UtteranceEnd"}). */
    void onSpeechFinal(Runnable handler);

    /** Terminal errors. */
    void onError(Consumer<Throwable> handler);

    /** Fired exactly once when the WebSocket is closed (any reason). */
    void onClose(Runnable handler);

    /** True when the WebSocket is open and accepting frames. */
    boolean isOpen();

    /** Graceful close — sends a Close frame and stops the KeepAlive task. */
    void close();

}
