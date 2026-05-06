package io.tacticl.business.agent.transcription;

import io.tacticl.client.whisper.client.WhisperClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhisperTranscriptionServiceTest {

    @Test
    void transcribeDelegatesToWhisperClient() {
        WhisperClient whisperClient = mock(WhisperClient.class);
        when(whisperClient.transcribe(any(byte[].class), eq("audio.m4a"), eq("audio/mp4")))
            .thenReturn("hello world");

        WhisperTranscriptionService service = new WhisperTranscriptionService(whisperClient);

        String result = service.transcribe(new byte[]{1, 2, 3}, "audio.m4a", "audio/mp4");

        assertEquals("hello world", result);
        verify(whisperClient).transcribe(any(byte[].class), eq("audio.m4a"), eq("audio/mp4"));
    }

    @Test
    void transcribeRejectsNullAudio() {
        WhisperClient whisperClient = mock(WhisperClient.class);
        WhisperTranscriptionService service = new WhisperTranscriptionService(whisperClient);

        assertThrows(IllegalArgumentException.class,
            () -> service.transcribe(null, "audio.m4a", "audio/mp4"));
    }

    @Test
    void transcribeRejectsEmptyAudio() {
        WhisperClient whisperClient = mock(WhisperClient.class);
        WhisperTranscriptionService service = new WhisperTranscriptionService(whisperClient);

        assertThrows(IllegalArgumentException.class,
            () -> service.transcribe(new byte[0], "audio.m4a", "audio/mp4"));
    }

    @Test
    void transcribeRejectsBlankFilename() {
        WhisperClient whisperClient = mock(WhisperClient.class);
        WhisperTranscriptionService service = new WhisperTranscriptionService(whisperClient);

        assertThrows(IllegalArgumentException.class,
            () -> service.transcribe(new byte[]{1}, "  ", "audio/mp4"));
    }

    @Test
    void transcribeRejectsBlankContentType() {
        WhisperClient whisperClient = mock(WhisperClient.class);
        WhisperTranscriptionService service = new WhisperTranscriptionService(whisperClient);

        assertThrows(IllegalArgumentException.class,
            () -> service.transcribe(new byte[]{1}, "audio.m4a", null));
    }

    @Test
    void implementsTranscriptionServiceInterface() {
        WhisperClient whisperClient = mock(WhisperClient.class);
        TranscriptionService service = new WhisperTranscriptionService(whisperClient);

        // Compile-time check: WhisperTranscriptionService is a TranscriptionService.
        // Runtime check: a non-null instance.
        assertEquals(WhisperTranscriptionService.class, service.getClass());
    }

}
