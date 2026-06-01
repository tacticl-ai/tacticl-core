package io.tacticl.client.elevenlabs.dto;

/**
 * A single chunk of synthesized audio returned by ElevenLabs.
 *
 * @param data raw audio bytes encoded per {@link #format}
 * @param format short token describing the codec ({@code "mp3"} or {@code "pcm"})
 * @param sequenceNumber monotonic counter starting at 0 for the first chunk of a session
 * @param isFinal true when this is the last chunk for the current utterance
 */
public record AudioChunk(byte[] data, String format, int sequenceNumber, boolean isFinal) {
}
