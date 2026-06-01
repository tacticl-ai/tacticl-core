package io.tacticl.client.deepgram.dto;

import java.util.List;

/**
 * Interim Deepgram transcript — {@code is_final: false}.
 *
 * @param text best alternative's transcript
 * @param confidence 0.0–1.0
 * @param wordTimings nullable; only present when Deepgram emits {@code words}
 * @param requestId Deepgram request id (correlates with logs)
 */
public record DeepgramPartialTranscript(String text,
                                        double confidence,
                                        List<WordTiming> wordTimings,
                                        String requestId) {
}
