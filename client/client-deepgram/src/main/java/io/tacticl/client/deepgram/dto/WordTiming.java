package io.tacticl.client.deepgram.dto;

/**
 * Per-word timing entry from a Deepgram transcript.
 *
 * @param word the recognized word
 * @param start seconds from stream start
 * @param end seconds from stream start
 * @param confidence 0.0–1.0
 */
public record WordTiming(String word, double start, double end, double confidence) {
}
