package io.tacticl.client.elevenlabs;

import io.cidadel.framework.exception.CidadelException;
import io.tacticl.client.elevenlabs.client.ElevenLabsClient;
import io.tacticl.client.elevenlabs.client.ElevenLabsClient.StreamingSession;
import io.tacticl.client.elevenlabs.client.ElevenLabsSession;
import io.tacticl.client.elevenlabs.client.ElevenLabsSessionConfig;
import io.tacticl.client.elevenlabs.config.ElevenLabsConfig;
import io.tacticl.client.elevenlabs.dto.AudioChunk;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ElevenLabsClientTest {

    private ElevenLabsConfig config;
    private JsonMapper mapper;

    @BeforeEach
    void setUp() {
        config = new ElevenLabsConfig();
        config.setApiKey("test-key");
        config.setApiBaseUrl("wss://api.elevenlabs.io");
        config.setModel("eleven_turbo_v2");
        config.setDefaultVoiceId("voice-abc");
        config.setDefaultOutputFormat("mp3_44100_128");
        mapper = JsonMapper.builder().build();
    }

    @Test
    void openRejectsWhenApiKeyMissing() {
        ElevenLabsConfig blank = new ElevenLabsConfig();
        ElevenLabsClient client = new ElevenLabsClient(blank, HttpClient.newHttpClient());

        CidadelException ex = assertThrows(CidadelException.class,
            () -> client.open(ElevenLabsSessionConfig.defaults()));
        assertEquals("elevenlabs-session-not-configured", ex.getErrorDetails().getPropertyKey());
    }

    @Test
    void buildInitFrameContainsVoiceSettingsAndApiKey() throws Exception {
        ElevenLabsClient client = new ElevenLabsClient(config, HttpClient.newHttpClient());
        ElevenLabsSessionConfig sc = new ElevenLabsSessionConfig("voice-xyz", 0.4, 0.8, "calm", "mp3_44100_128");

        String init = client.buildInitFrame(sc);
        JsonNode node = mapper.readTree(init);

        assertEquals(" ", node.get("text").asString(), "init must seed with space text");
        assertEquals(0.4, node.get("voice_settings").get("stability").asDouble(), 0.0001);
        assertEquals(0.8, node.get("voice_settings").get("similarity_boost").asDouble(), 0.0001);
        assertEquals("calm", node.get("voice_settings").get("style").asString());
        assertEquals("test-key", node.get("xi_api_key").asString());
        assertNotNull(node.get("generation_config"));
    }

    @Test
    void buildInitFrameOmitsStyleWhenNull() throws Exception {
        ElevenLabsClient client = new ElevenLabsClient(config, HttpClient.newHttpClient());

        String init = client.buildInitFrame(ElevenLabsSessionConfig.defaults());
        JsonNode node = mapper.readTree(init);

        assertFalse(node.get("voice_settings").has("style"),
            "style must be omitted when null to avoid sending a literal null");
    }

    @Test
    void sendTextChunkSerializesTextFrame() {
        WebSocket ws = mockWebSocket();
        StreamingSession session = newAttachedSession();
        session.attach(ws);

        session.sendTextChunk("hello there");

        ArgumentCaptor<CharSequence> captor = ArgumentCaptor.forClass(CharSequence.class);
        verify(ws, atLeastOnce()).sendText(captor.capture(), anyBoolean());
        JsonNode frame = readJson(captor.getValue().toString());
        assertEquals("hello there", frame.get("text").asString());
    }

    @Test
    void flushSendsEmptyTextWithFlushTrue() {
        WebSocket ws = mockWebSocket();
        StreamingSession session = newAttachedSession();
        session.attach(ws);

        session.flush();

        ArgumentCaptor<CharSequence> captor = ArgumentCaptor.forClass(CharSequence.class);
        verify(ws, atLeastOnce()).sendText(captor.capture(), anyBoolean());
        JsonNode frame = readJson(captor.getValue().toString());
        assertEquals("", frame.get("text").asString());
        assertTrue(frame.get("flush").asBoolean());
    }

    @Test
    void closeAbortsWithoutSendingEndFrame() {
        WebSocket ws = mockWebSocket();
        StreamingSession session = newAttachedSession();
        session.attach(ws);

        session.close();

        // Teardown is abort-only: a graceful EOS sendText races the in-flight send-serialization
        // chain (a new turn's stop()/barge-in), interleaving frames on the wire so ElevenLabs
        // rejects the stream (UPSTREAM_ERROR). On supersede we discard remaining audio anyway,
        // so close() must NOT send a terminator — it aborts.
        verify(ws, never()).sendText(any(CharSequence.class), anyBoolean());
        verify(ws).abort();
        assertFalse(session.isOpen());
    }

    @Test
    void closeIsNonBlockingEvenWhenSendFutureNeverCompletes() {
        WebSocket ws = mock(WebSocket.class);
        // Send future that never completes — mimics in-flight audio TTS.
        when(ws.sendText(any(CharSequence.class), anyBoolean())).thenReturn(new CompletableFuture<>());
        when(ws.isOutputClosed()).thenReturn(false);
        when(ws.isInputClosed()).thenReturn(false);

        StreamingSession session = newAttachedSession();
        session.attach(ws);
        session.sendTextChunk("mid-utterance"); // queue a hanging send
        long start = System.nanoTime();
        session.close();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 200, "close() must return quickly even with hanging send; took " + elapsedMs + "ms");
        verify(ws).abort();
    }

    @Test
    void inboundFrameDecodesBase64AudioAndIncrementsSequence() {
        StreamingSession session = newAttachedSession();
        List<AudioChunk> received = new ArrayList<>();
        session.onAudioChunk(received::add);

        byte[] payload1 = new byte[]{1, 2, 3};
        byte[] payload2 = new byte[]{4, 5, 6};
        session.handleInbound("{\"audio\":\"" + Base64.getEncoder().encodeToString(payload1) + "\",\"isFinal\":false}");
        session.handleInbound("{\"audio\":\"" + Base64.getEncoder().encodeToString(payload2) + "\",\"isFinal\":false}");

        assertEquals(2, received.size());
        assertArrayEquals(payload1, received.get(0).data());
        assertEquals("mp3", received.get(0).format());
        assertEquals(0, received.get(0).sequenceNumber());
        assertEquals(1, received.get(1).sequenceNumber());
        assertFalse(received.get(0).isFinal());
    }

    @Test
    void finalFrameFiresOnDoneOnce() {
        StreamingSession session = newAttachedSession();
        AtomicBoolean done = new AtomicBoolean(false);
        session.onDone(() -> done.set(true));

        session.handleInbound("{\"audio\":\"" + Base64.getEncoder().encodeToString(new byte[]{9}) + "\",\"isFinal\":true}");

        assertTrue(done.get(), "isFinal=true must trigger onDone handler");
    }

    @Test
    void errorFrameInvokesErrorHandler() {
        StreamingSession session = newAttachedSession();
        AtomicReference<Throwable> seen = new AtomicReference<>();
        session.onError(seen::set);

        session.handleInbound("{\"error\":\"voice_not_found\",\"message\":\"voice missing\"}");

        Throwable t = seen.get();
        assertNotNull(t, "error frame must reach the error handler");
        assertTrue(t instanceof CidadelException, "error must be a CidadelException; got " + t.getClass());
        CidadelException ce = (CidadelException) t;
        assertEquals("elevenlabs-upstream-error", ce.getErrorDetails().getPropertyKey());
    }

    @Test
    void onTextBuffersPartialsThenDispatches() {
        StreamingSession session = newAttachedSession();
        AtomicReference<AudioChunk> seen = new AtomicReference<>();
        session.onAudioChunk(seen::set);

        WebSocket ws = mockWebSocket();
        String fullJson = "{\"audio\":\"" + Base64.getEncoder().encodeToString(new byte[]{7, 7}) + "\"}";
        int mid = fullJson.length() / 2;

        session.onText(ws, fullJson.substring(0, mid), false);
        // No audio yet — frame not complete.
        assertEquals(null, seen.get());
        session.onText(ws, fullJson.substring(mid), true);

        assertNotNull(seen.get(), "partial WS frames must coalesce before dispatch");
        assertArrayEquals(new byte[]{7, 7}, seen.get().data());
    }

    @Test
    void binaryFramesAreInertButRequestMore() {
        StreamingSession session = newAttachedSession();
        WebSocket ws = mockWebSocket();

        // Should not throw; should request more frames.
        session.onBinary(ws, ByteBuffer.wrap("ignored".getBytes(StandardCharsets.UTF_8)), true);

        verify(ws).request(1L);
    }

    @Test
    void closeIsIdempotent() {
        WebSocket ws = mockWebSocket();
        StreamingSession session = newAttachedSession();
        session.attach(ws);

        session.close();
        session.close(); // should not throw or double-abort

        verify(ws).abort();
    }

    @Test
    void sendTextChunkOnClosedSessionThrows() {
        StreamingSession session = newAttachedSession();
        // Do NOT attach() — session not open.

        CidadelException ex = assertThrows(CidadelException.class,
            () -> session.sendTextChunk("nope"));
        assertEquals("elevenlabs-session-closed", ex.getErrorDetails().getPropertyKey());
    }

    // -- helpers ----------------------------------------------------------

    private StreamingSession newAttachedSession() {
        return new StreamingSession("mp3_44100_128", mapper);
    }

    private WebSocket mockWebSocket() {
        WebSocket ws = mock(WebSocket.class);
        when(ws.sendText(any(CharSequence.class), anyBoolean()))
            .thenReturn(CompletableFuture.completedFuture(ws));
        when(ws.isOutputClosed()).thenReturn(false);
        when(ws.isInputClosed()).thenReturn(false);
        return ws;
    }

    private JsonNode readJson(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ElevenLabsSession asSession(StreamingSession s) {
        return s;
    }

}
