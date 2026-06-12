package io.tacticl.client.deepgram.client;

import io.cidadel.framework.exception.CidadelException;
import io.tacticl.client.deepgram.config.DeepgramConfig;
import io.tacticl.client.deepgram.dto.DeepgramFinalTranscript;
import io.tacticl.client.deepgram.dto.DeepgramPartialTranscript;
import io.tacticl.client.deepgram.dto.DeepgramSessionConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeepgramClientTest {

    private DeepgramConfig config;

    @BeforeEach
    void setUp() {
        config = new DeepgramConfig();
        config.setApiBaseUrl("wss://api.deepgram.com");
        config.setModel("nova-2");
        config.setEndpointingMs(300);
        config.setSampleRate(16000);
    }

    /* --------------- buildEndpoint URL contract --------------- */

    @Test
    void buildEndpointIncludesAllStreamingParams() {
        config.setApiKey("dg-test");
        DeepgramClient client = new DeepgramClient(config, HttpClient.newHttpClient());

        URI uri = client.buildEndpoint(DeepgramSessionConfig.defaults());

        String s = uri.toString();
        assertTrue(s.startsWith("wss://api.deepgram.com/v1/listen?"), "wrong base: " + s);
        assertTrue(s.contains("model=nova-2"), "missing model");
        assertTrue(s.contains("encoding=linear16"), "missing encoding");
        assertTrue(s.contains("sample_rate=16000"), "missing sample_rate");
        assertTrue(s.contains("channels=1"), "missing channels");
        assertTrue(s.contains("interim_results=true"), "missing interim_results");
        assertTrue(s.contains("endpointing=300"), "missing endpointing");
        assertTrue(s.contains("vad_events=true"), "missing vad_events");
        assertTrue(s.contains("language=en-US"), "missing language");
    }

    /* --------------- open() guardrails --------------- */

    @Test
    void openThrowsWhenApiKeyMissing() {
        // No setApiKey
        DeepgramClient client = new DeepgramClient(config, HttpClient.newHttpClient());

        CidadelException ex = assertThrows(CidadelException.class,
            () -> client.open(DeepgramSessionConfig.defaults()));
        assertEquals("deepgram-not-configured", ex.getErrorDetails().getPropertyKey());
    }

    /* --------------- Frame parsing — handleTextFrame() --------------- */

    @Test
    void interimResultsFirePartialHandler() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();
        AtomicReference<DeepgramPartialTranscript> got = new AtomicReference<>();
        AtomicInteger finals = new AtomicInteger();
        session.onPartialTranscript(got::set);
        session.onFinalTranscript(t -> finals.incrementAndGet());

        String json = """
            {
              "type": "Results",
              "channel": {
                "alternatives": [
                  { "transcript": "hello wor", "confidence": 0.82 }
                ]
              },
              "is_final": false,
              "speech_final": false,
              "request_id": "req-1"
            }
            """;
        session.handleTextFrame(json);

        assertNotNull(got.get());
        assertEquals("hello wor", got.get().text());
        assertEquals(0.82, got.get().confidence(), 1e-6);
        assertEquals("req-1", got.get().requestId());
        assertEquals(0, finals.get(), "final handler should not fire for interim");
        assertNull(got.get().wordTimings());
    }

    @Test
    void finalSpeechFinalFiresFinalHandlerOnly() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();
        AtomicReference<DeepgramFinalTranscript> got = new AtomicReference<>();
        AtomicInteger partials = new AtomicInteger();
        session.onFinalTranscript(got::set);
        session.onPartialTranscript(p -> partials.incrementAndGet());

        String json = """
            {
              "type": "Results",
              "channel": {
                "alternatives": [
                  {
                    "transcript": "hello world",
                    "confidence": 0.97,
                    "words": [
                      {"word":"hello","start":0.0,"end":0.4,"confidence":0.98},
                      {"word":"world","start":0.5,"end":0.9,"confidence":0.96}
                    ]
                  }
                ]
              },
              "is_final": true,
              "speech_final": true,
              "request_id": "req-2"
            }
            """;
        session.handleTextFrame(json);

        assertNotNull(got.get());
        assertEquals("hello world", got.get().text());
        assertEquals(0.97, got.get().confidence(), 1e-6);
        assertNotNull(got.get().wordTimings());
        assertEquals(2, got.get().wordTimings().size());
        assertEquals("hello", got.get().wordTimings().get(0).word());
        assertEquals(0, partials.get(), "partial handler should not fire when speech_final");
    }

    @Test
    void isFinalWithoutSpeechFinalIsPartialNotFinal() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();
        AtomicInteger partials = new AtomicInteger();
        AtomicInteger finals = new AtomicInteger();
        session.onPartialTranscript(p -> partials.incrementAndGet());
        session.onFinalTranscript(f -> finals.incrementAndGet());

        String json = """
            {
              "type": "Results",
              "channel": {"alternatives": [{"transcript": "hi", "confidence": 0.9}]},
              "is_final": true,
              "speech_final": false
            }
            """;
        session.handleTextFrame(json);

        assertEquals(1, partials.get(), "is_final without speech_final is treated as partial");
        assertEquals(0, finals.get());
    }

    @Test
    void speechStartedFiresHandler() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();
        AtomicBoolean fired = new AtomicBoolean(false);
        session.onSpeechStarted(() -> fired.set(true));

        session.handleTextFrame("{\"type\":\"SpeechStarted\"}");

        assertTrue(fired.get());
    }

    @Test
    void utteranceEndFiresSpeechFinalHandler() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();
        AtomicBoolean fired = new AtomicBoolean(false);
        session.onSpeechFinal(() -> fired.set(true));

        session.handleTextFrame("{\"type\":\"UtteranceEnd\"}");

        assertTrue(fired.get());
    }

    /* --------------- Utterance accumulation — segmented finals --------------- */

    @Test
    void utteranceEndFlushesBufferedIsFinalSegmentsAsFinal() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();
        AtomicReference<DeepgramFinalTranscript> got = new AtomicReference<>();
        session.onFinalTranscript(got::set);

        // VAD endpointing never set speech_final (noisy room) — the utterance
        // arrives as an is_final segment followed by UtteranceEnd only.
        session.handleTextFrame(resultsFrame("what's going on", true, false));
        session.handleTextFrame("{\"type\":\"UtteranceEnd\"}");

        assertNotNull(got.get(), "UtteranceEnd must flush buffered is_final segments");
        assertEquals("what's going on", got.get().text());

        // Buffer must be consumed — a second UtteranceEnd is a no-op.
        got.set(null);
        session.handleTextFrame("{\"type\":\"UtteranceEnd\"}");
        assertNull(got.get(), "flush must clear the segment buffer");
    }

    @Test
    void blankSpeechFinalTailMergesWithBufferedSegments() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();
        AtomicReference<DeepgramFinalTranscript> got = new AtomicReference<>();
        session.onFinalTranscript(got::set);

        session.handleTextFrame(resultsFrame("deploy the fix", true, false));
        session.handleTextFrame(resultsFrame("to production", true, false));
        // Trailing silence segment: speech_final fires with an empty transcript.
        session.handleTextFrame(resultsFrame("", true, true));

        assertNotNull(got.get(), "speech_final must flush even with a blank tail");
        assertEquals("deploy the fix to production", got.get().text());
    }

    @Test
    void speechFinalTailTextIsAppendedAfterBufferedSegments() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();
        AtomicReference<DeepgramFinalTranscript> got = new AtomicReference<>();
        session.onFinalTranscript(got::set);

        session.handleTextFrame(resultsFrame("build a health", true, false));
        session.handleTextFrame(resultsFrame("endpoint", true, true));

        assertEquals("build a health endpoint", got.get().text());
    }

    @Test
    void utteranceEndWithoutBufferedSegmentsFiresNoFinal() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();
        AtomicInteger finals = new AtomicInteger();
        session.onFinalTranscript(f -> finals.incrementAndGet());

        session.handleTextFrame("{\"type\":\"UtteranceEnd\"}");

        assertEquals(0, finals.get(), "silence-only turn must not dispatch a final");
    }

    @Test
    void markClosedFlushesBufferedSegmentsBeforeCloseHandler() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();
        List<String> order = new ArrayList<>();
        session.onFinalTranscript(f -> order.add("final:" + f.text()));
        session.onClose(() -> order.add("close"));

        // Operator released the button; CloseStream drain returned one more
        // is_final segment but the socket closed before any speech_final.
        session.handleTextFrame(resultsFrame("last words", true, false));
        session.markClosed();

        assertEquals(List.of("final:last words", "close"), order,
            "pending utterance must flush as final before the close handler runs");
    }

    @Test
    void errorFrameFiresErrorHandler() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();
        AtomicReference<Throwable> caught = new AtomicReference<>();
        session.onError(caught::set);

        session.handleTextFrame("{\"type\":\"Error\",\"description\":\"bad audio\"}");

        assertNotNull(caught.get(), "error handler should fire on Deepgram Error frame");
        assertTrue(caught.get() instanceof CidadelException, "should wrap as CidadelException");
        CidadelException cex = (CidadelException) caught.get();
        assertEquals("deepgram-connect-failed", cex.getErrorDetails().getPropertyKey());
        // Deepgram's description text is carried via the exception args[] (CidadelException
        // does not concatenate args into getMessage() — verify via getArgs()).
        Object[] args = cex.getArgs();
        assertNotNull(args);
        assertTrue(args.length > 0 && "bad audio".equals(args[0]),
            "description should be propagated as exception arg[0]");
    }

    @Test
    void malformedJsonFiresErrorHandler() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();
        AtomicReference<Throwable> caught = new AtomicReference<>();
        session.onError(caught::set);

        session.handleTextFrame("{not json");

        assertNotNull(caught.get());
    }

    @Test
    void unknownTypeIsIgnored() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();
        AtomicReference<Throwable> caught = new AtomicReference<>();
        AtomicInteger partials = new AtomicInteger();
        AtomicInteger finals = new AtomicInteger();
        session.onError(caught::set);
        session.onPartialTranscript(p -> partials.incrementAndGet());
        session.onFinalTranscript(f -> finals.incrementAndGet());

        session.handleTextFrame("{\"type\":\"Metadata\",\"request_id\":\"abc\"}");
        session.handleTextFrame("{\"type\":\"Brand_New_Future_Event\"}");

        assertNull(caught.get());
        assertEquals(0, partials.get());
        assertEquals(0, finals.get());
    }

    @Test
    void fragmentedTextFramesAreBufferedUntilLast() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();
        AtomicReference<DeepgramFinalTranscript> got = new AtomicReference<>();
        session.onFinalTranscript(got::set);

        String json = "{\"type\":\"Results\",\"channel\":{\"alternatives\":["
            + "{\"transcript\":\"chunked\",\"confidence\":0.99}]}"
            + ",\"is_final\":true,\"speech_final\":true}";
        // simulate three fragments
        int third = json.length() / 3;
        session.onTextFragment(json.substring(0, third), false);
        session.onTextFragment(json.substring(third, 2 * third), false);
        assertNull(got.get(), "handler should not fire until last==true");
        session.onTextFragment(json.substring(2 * third), true);

        assertNotNull(got.get());
        assertEquals("chunked", got.get().text());
    }

    /* --------------- sendAudio + lifecycle --------------- */

    @Test
    @SuppressWarnings("unchecked")
    void sendAudioWritesBinaryFrameToWebSocket() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();
        WebSocket ws = mock(WebSocket.class);
        lenient().when(ws.isOutputClosed()).thenReturn(false);
        lenient().when(ws.sendBinary(any(ByteBuffer.class), anyBoolean()))
            .thenReturn((CompletableFuture<WebSocket>) CompletableFuture.completedFuture(ws));
        session.attachWebSocket(ws);

        byte[] pcm = new byte[]{1, 2, 3, 4, 5, 6};
        session.sendAudio(pcm);

        verify(ws, times(1)).sendBinary(ByteBuffer.wrap(pcm), true);
    }

    @Test
    void sendAudioRejectsNullOrEmpty() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();
        session.attachWebSocket(mock(WebSocket.class));

        CidadelException ex1 = assertThrows(CidadelException.class, () -> session.sendAudio(null));
        assertEquals("deepgram-invalid-frame", ex1.getErrorDetails().getPropertyKey());
        CidadelException ex2 = assertThrows(CidadelException.class, () -> session.sendAudio(new byte[0]));
        assertEquals("deepgram-invalid-frame", ex2.getErrorDetails().getPropertyKey());
    }

    @Test
    void sendAudioWhenNotAttachedThrowsSessionClosed() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();

        CidadelException ex = assertThrows(CidadelException.class,
            () -> session.sendAudio(new byte[]{1, 2}));
        assertEquals("deepgram-session-closed", ex.getErrorDetails().getPropertyKey());
    }

    @Test
    void isOpenReflectsWebSocketState() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();
        assertFalse(session.isOpen(), "not open before WS attached");

        WebSocket ws = mock(WebSocket.class);
        when(ws.isOutputClosed()).thenReturn(false);
        session.attachWebSocket(ws);
        assertTrue(session.isOpen());

        // simulate output closed
        when(ws.isOutputClosed()).thenReturn(true);
        assertFalse(session.isOpen());
    }

    @Test
    @SuppressWarnings("unchecked")
    void closeIsIdempotentAndFiresCloseHandlerOnce() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();
        WebSocket ws = mock(WebSocket.class);
        lenient().when(ws.sendClose(anyInt(), anyString()))
            .thenReturn((CompletableFuture<WebSocket>) CompletableFuture.completedFuture(ws));
        session.attachWebSocket(ws);

        AtomicInteger closeCount = new AtomicInteger();
        session.onClose(closeCount::incrementAndGet);

        session.close();
        session.close(); // idempotent

        assertEquals(1, closeCount.get(), "close handler should fire exactly once");
        verify(ws, times(1)).sendClose(WebSocket.NORMAL_CLOSURE, "client-close");
    }

    /* --------------- handler-registration safety --------------- */

    @Test
    void nullHandlersAreIgnored() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();
        // none of these should NPE later when frames arrive
        session.onPartialTranscript(null);
        session.onFinalTranscript(null);
        session.onSpeechStarted(null);
        session.onSpeechFinal(null);
        session.onError(null);
        session.onClose(null);

        // and a Results frame still parses cleanly
        session.handleTextFrame("{\"type\":\"Results\",\"channel\":{\"alternatives\":["
            + "{\"transcript\":\"x\",\"confidence\":0.5}]},\"is_final\":false,\"speech_final\":false}");
    }

    @Test
    void multiplePartialsThenFinalDeliveredInOrder() {
        DeepgramSessionImpl session = new DeepgramSessionImpl();
        List<String> seq = new ArrayList<>();
        session.onPartialTranscript(p -> seq.add("P:" + p.text()));
        session.onFinalTranscript(f -> seq.add("F:" + f.text()));

        session.handleTextFrame(resultsFrame("hel", false, false));
        session.handleTextFrame(resultsFrame("hello", false, false));
        session.handleTextFrame(resultsFrame("hello world", true, true));

        assertEquals(List.of("P:hel", "P:hello", "F:hello world"), seq);
    }

    private static String resultsFrame(String text, boolean isFinal, boolean speechFinal) {
        return String.format(
            "{\"type\":\"Results\",\"channel\":{\"alternatives\":[{\"transcript\":\"%s\",\"confidence\":0.9}]},"
                + "\"is_final\":%s,\"speech_final\":%s}",
            text, isFinal, speechFinal);
    }

}
