package io.tacticl.business.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.tacticl.business.pipeline.ingress.ChannelType;
import io.tacticl.business.pipeline.ingress.RunOrigin;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the conversation handler seam: the {@link ConversationEngine}
 * produces the reply (mocked), the handler streams it into the session, executes
 * the {@code start_pipeline} skill via the local trigger path, speaks on done, and
 * records memory. A real registry holds a session with mocked TTS/STT legs.
 */
class VoiceConversationTurnHandlerTest {

    private VoiceSessionRegistry registry;
    private ConversationEngine engine;
    private VoiceSessionService voiceSessionService;
    private ElevenLabsTtsBridge tts;
    private RecordingOutbound out;
    private VoiceSession session;
    private VoiceConversationTurnHandler handler;

    private static final String SESSION_ID = "sess-1";
    private static final String USER_ID = "user-1";

    @BeforeEach
    void setUp() {
        registry = new VoiceSessionRegistry();
        engine = mock(ConversationEngine.class);
        voiceSessionService = mock(VoiceSessionService.class);
        tts = mock(ElevenLabsTtsBridge.class);
        out = new RecordingOutbound();
        session = new VoiceSession(SESSION_ID, USER_ID, out, mock(DeepgramSttBridge.class), tts);
        registry.register(session);
        handler = new VoiceConversationTurnHandler(registry, engine, voiceSessionService, new VoiceProperties());
    }

    private static RunOrigin voiceOrigin(String sessionId) {
        return new RunOrigin(ChannelType.VOICE, "voice-default", sessionId, null);
    }

    /** Make the mocked engine drive a scripted sequence onto the sink it receives. */
    private void engineDrives(Consumer<ConversationSink> script) {
        doAnswer(inv -> {
            ConversationSink sink = inv.getArgument(1);
            script.accept(sink);
            return null;
        }).when(engine).converse(any(ConversationContext.class), any(ConversationSink.class));
    }

    @Test
    void handleTurn_streamsReply_speaksAndRemembers() {
        engineDrives(sink -> {
            sink.onToken("Sure — ");
            sink.onToken("what problem are you solving?");
            sink.onDone();
        });

        handler.handleTurn(USER_ID, voiceOrigin(SESSION_ID), "I want a health check", true);

        verify(engine).converse(any(ConversationContext.class), any(ConversationSink.class));
        assertThat(out.framesOfType("transcript")).anyMatch(f -> "assistant".equals(f.get("role")));
        assertThat(out.lastState()).contains("speaking");
        verify(tts).speak("Sure — what problem are you solving?");
        assertThat(session.history()).containsExactly(
            new VoiceSession.Utterance("user", "I want a health check"),
            new VoiceSession.Utterance("assistant", "Sure — what problem are you solving?"));
    }

    @Test
    void handleTurn_startPipelineToolUse_routesToLocalTrigger() {
        engineDrives(sink -> {
            sink.onToken("Starting that now.");
            sink.onToolUse("start_pipeline", "{\"sparkInput\":\"build a /health endpoint\"}");
            sink.onDone();
        });

        handler.handleTurn(USER_ID, voiceOrigin(SESSION_ID), "build a health endpoint", true);

        // No repoUrl in the tool input → null (EntryPoint fallback downstream).
        verify(voiceSessionService).startPipelineFromConversation(session, "build a /health endpoint", null);
        verify(tts).speak("Starting that now.");
    }

    @Test
    void handleTurn_startPipelineWithRepoUrl_threadsProvisionedRepo() {
        engineDrives(sink -> {
            sink.onToolUse("start_pipeline",
                "{\"sparkInput\":\"build a /health endpoint\",\"repoUrl\":\"https://github.com/tacticl-ai/health.git\"}");
            sink.onDone();
        });

        handler.handleTurn(USER_ID, voiceOrigin(SESSION_ID), "yes go ahead", true);

        verify(voiceSessionService).startPipelineFromConversation(
            session, "build a /health endpoint", "https://github.com/tacticl-ai/health.git");
    }

    @Test
    void handleTurn_toolOnlyTurn_recordsUserAndActionInMemory() {
        // A tool-only turn (start_pipeline, no spoken reply) must still leave both the
        // user utterance and the action in history — otherwise the next turn is blind
        // to what just happened and the brain re-asks for confirmation forever.
        engineDrives(sink -> {
            sink.onToolUse("start_pipeline", "{\"sparkInput\":\"build a /health endpoint\"}");
            sink.onDone();
        });

        handler.handleTurn(USER_ID, voiceOrigin(SESSION_ID), "yes go ahead", true);

        verify(voiceSessionService).startPipelineFromConversation(session, "build a /health endpoint", null);
        assertThat(session.history()).containsExactly(
            new VoiceSession.Utterance("user", "yes go ahead"),
            new VoiceSession.Utterance("assistant", "Starting the build pipeline now."));
    }

    @Test
    void handleTurn_threadsPersonaIntoAssistantHistory() {
        engineDrives(sink -> {
            sink.onPersona("product-manager");
            sink.onToken("What problem are you solving?");
            sink.onDone();
        });

        handler.handleTurn(USER_ID, voiceOrigin(SESSION_ID), "I want a feature", true);

        assertThat(session.history()).containsExactly(
            new VoiceSession.Utterance("user", "I want a feature", null),
            new VoiceSession.Utterance("assistant", "What problem are you solving?", "product-manager"));
    }

    @Test
    void handleTurn_unknownToolUse_isIgnored() {
        engineDrives(sink -> {
            sink.onToolUse("web_search", "{\"query\":\"x\"}");
            sink.onToken("Here's what I think.");
            sink.onDone();
        });

        handler.handleTurn(USER_ID, voiceOrigin(SESSION_ID), "hi", true);

        verifyNoInteractions(voiceSessionService);
        verify(tts).speak("Here's what I think.");
    }

    @Test
    void handleTurn_noLiveSession_isNoOp() {
        handler.handleTurn(USER_ID, voiceOrigin("unknown-session"), "hello", true);

        verifyNoInteractions(engine);
        verifyNoInteractions(tts);
        verifyNoInteractions(voiceSessionService);
        assertThat(out.controls).isEmpty();
    }

    @Test
    void handleTurn_engineError_emitsErrorFrameAndSettlesIdle() {
        engineDrives(sink -> sink.onError("upstream down"));

        handler.handleTurn(USER_ID, voiceOrigin(SESSION_ID), "hello", true);

        assertThat(out.framesOfType("error")).isNotEmpty();
        assertThat(out.lastState()).contains("idle");
        verify(tts, never()).speak(anyString());
        assertThat(session.history()).isEmpty();
    }

    @Test
    void handleTurn_engineThrows_emitsErrorFrame_neverThrows() {
        doAnswer(inv -> {
            throw new RuntimeException("boom");
        }).when(engine).converse(any(ConversationContext.class), any(ConversationSink.class));

        handler.handleTurn(USER_ID, voiceOrigin(SESSION_ID), "hello", true);

        assertThat(out.framesOfType("error")).isNotEmpty();
        assertThat(out.lastState()).contains("idle");
        assertThat(session.history()).isEmpty();
    }

    @Test
    void handleTurn_blankText_isNoOp() {
        handler.handleTurn(USER_ID, voiceOrigin(SESSION_ID), "   ", true);

        verifyNoInteractions(engine);
        verifyNoInteractions(tts);
        verifyNoInteractions(voiceSessionService);
    }
}
