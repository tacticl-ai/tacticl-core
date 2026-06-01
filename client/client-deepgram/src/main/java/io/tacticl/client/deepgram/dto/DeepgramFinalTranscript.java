package io.tacticl.client.deepgram.dto;

import java.util.List;

/**
 * Final Deepgram transcript — {@code is_final: true && speech_final: true}.
 * Triggers the {@code onUserTranscript} signal to the session workflow per SAD §5.2.
 *
 * @param text best alternative's transcript
 * @param confidence 0.0–1.0
 * @param wordTimings nullable
 * @param requestId Deepgram request id
 */
public record DeepgramFinalTranscript(String text,
                                      double confidence,
                                      List<WordTiming> wordTimings,
                                      String requestId) {
}
