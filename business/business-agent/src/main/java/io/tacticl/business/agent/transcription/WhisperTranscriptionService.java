package io.tacticl.business.agent.transcription;

import io.tacticl.client.whisper.client.WhisperClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Whisper-backed implementation of {@link TranscriptionService}.
 *
 * <p>Activated only when {@code tacticl.whisper.enabled=true} so that environments
 * without the OpenAI key configured can still start cleanly.
 */
@Service
@ConditionalOnProperty(name = "tacticl.whisper.enabled", havingValue = "true")
public class WhisperTranscriptionService implements TranscriptionService {

    private final WhisperClient whisperClient;

    public WhisperTranscriptionService(WhisperClient whisperClient) {
        this.whisperClient = whisperClient;
    }

    @Override
    public String transcribe(byte[] audio, String filename, String contentType) {
        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("audio must be non-null and non-empty");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must be non-blank");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must be non-blank");
        }
        return whisperClient.transcribe(audio, filename, contentType);
    }

}
