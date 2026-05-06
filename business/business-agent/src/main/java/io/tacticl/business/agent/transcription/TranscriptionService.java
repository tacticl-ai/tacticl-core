package io.tacticl.business.agent.transcription;

/**
 * Server-side audio transcription used by the agent intake path.
 *
 * <p>Implementations turn raw audio bytes into a text transcription. Today there is
 * a single Whisper-backed implementation; additional providers may be plugged in
 * behind this interface without changing callers.
 */
public interface TranscriptionService {

    /**
     * Transcribe the given audio bytes into text.
     *
     * @param audio raw audio bytes (must be non-null and non-empty)
     * @param filename original filename including extension (e.g. {@code audio.m4a})
     * @param contentType MIME type of the audio (e.g. {@code audio/mp4})
     * @return transcribed text
     * @throws IllegalArgumentException if any argument is null/blank/empty
     */
    String transcribe(byte[] audio, String filename, String contentType);

}
