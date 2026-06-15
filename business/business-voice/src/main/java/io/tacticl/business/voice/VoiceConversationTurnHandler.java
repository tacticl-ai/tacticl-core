package io.tacticl.business.voice;

import io.tacticl.business.pipeline.ingress.ChannelType;
import io.tacticl.business.pipeline.ingress.ConversationTurnHandler;
import io.tacticl.business.pipeline.ingress.RunOrigin;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bridges a conversational ({@code CONVERSATION_TURN}) voice utterance to the
 * conversation brain and narrates the reply. The cognition lives behind the
 * {@link ConversationEngine} seam — in the arbiter on the primary path, or the
 * in‑JVM fallback locally. This class is transport glue only: resolve the speaking
 * session, hand the turn to the engine, stream the reply to the sphere, execute any
 * side‑effecting skill the persona invoked, and speak/settle on completion.
 *
 * <p>Implements the {@link ConversationTurnHandler} SPI that
 * {@code IngressDispatchService} calls for every non‑build turn. Gated on
 * {@code tacticl.voice.enabled} so absence ⇒ the dispatcher's "drop" behaviour.
 */
@Service
@ConditionalOnProperty(name = "tacticl.voice.enabled", havingValue = "true")
public class VoiceConversationTurnHandler implements ConversationTurnHandler {

    private static final Logger log = LoggerFactory.getLogger(VoiceConversationTurnHandler.class);

    private static final JsonMapper JSON = new JsonMapper();

    /** The one side‑effecting skill the persona can invoke in Phase 1. */
    private static final String SKILL_START_PIPELINE = "start_pipeline";

    private final VoiceSessionRegistry registry;

    private final ConversationEngine engine;

    private final VoiceSessionService voiceSessionService;

    private final VoiceProperties properties;

    public VoiceConversationTurnHandler(VoiceSessionRegistry registry,
                                        ConversationEngine engine,
                                        VoiceSessionService voiceSessionService,
                                        VoiceProperties properties) {
        this.registry = registry;
        this.engine = engine;
        this.voiceSessionService = voiceSessionService;
        this.properties = properties;
        log.info("Voice conversation brain wired: {}", engine.getClass().getSimpleName());
    }

    /** The voice plane serves the VOICE channel — the in-app voice sphere (web /chat) and voice. */
    @Override
    public boolean supports(ChannelType channel) {
        return channel == ChannelType.VOICE;
    }

    @Override
    public void handleTurn(String tacticlUserId, RunOrigin origin, String text, boolean canDispatch) {
        if (origin == null || text == null || text.isBlank()) {
            return;
        }
        // destinationHandle == voice sessionId (VoiceSessionService#originFor).
        Optional<VoiceSession> sessionOpt = registry.bySessionId(origin.destinationHandle());
        if (sessionOpt.isEmpty()) {
            log.warn("Conversation turn with no live voice session session={} user={}",
                     origin.destinationHandle(), tacticlUserId);
            return;
        }
        VoiceSession session = sessionOpt.get();

        ConversationContext ctx = new ConversationContext(
            properties.getProductId(), tacticlUserId, session.sessionId(),
            "t-" + UUID.randomUUID(), text, session.history(), canDispatch);
        SessionSink sink = new SessionSink(session, text);

        // The handler runs on the STT final-transcript thread and must NOT throw —
        // contain engine setup failures and surface them cleanly. (Streaming engines
        // return immediately; their later failures arrive via sink.onError.)
        try {
            engine.converse(ctx, sink);
        } catch (Exception e) {
            log.warn("Conversation engine failed session={}: {}", session.sessionId(), e.toString());
            sink.onError("Sorry — I hit a snag answering that. Try me again.");
        }
    }

    /**
     * Accumulates the streamed reply, streams a live partial transcript to the comms
     * log, executes side‑effecting skills, and on completion speaks the reply +
     * records the exchange in session memory. One instance per turn (single‑threaded
     * per turn — gRPC serializes a stream's callbacks).
     */
    private final class SessionSink implements ConversationSink {

        private final VoiceSession session;
        private final String userText;
        private final String transcriptId = "a-" + UUID.randomUUID();
        private final StringBuilder reply = new StringBuilder();

        /** Persona the brain chose for this turn (arbiter path) — recorded with the reply. */
        private volatile String personaId;
        /** Whether this turn fired the start_pipeline skill (drives a tool-only memory marker). */
        private boolean pipelineStarted;
        /** Guards against recording the user utterance more than once per turn. */
        private boolean userRecorded;

        SessionSink(VoiceSession session, String userText) {
            this.session = session;
            this.userText = userText;
        }

        @Override
        public void onPersona(String personaId) {
            if (personaId != null && !personaId.isBlank()) {
                this.personaId = personaId;
            }
        }

        @Override
        public void onToken(String delta) {
            if (delta == null || delta.isEmpty()) {
                return;
            }
            reply.append(delta);
            // Stream the reply into the comms log as it forms (partial:true).
            session.outbound().sendControl(
                VoiceFrames.transcript("assistant", transcriptId, reply.toString(), true));
        }

        @Override
        public void onToolUse(String name, String inputJson) {
            if (SKILL_START_PIPELINE.equals(name)) {
                String sparkInput = sparkInputOf(inputJson);
                // The analyst's create_repo skill (executed in the arbiter) provisions the repo and
                // hands its URL back to the model, which passes it here. Null ⇒ EntryPoint fallback.
                String repoUrl = repoUrlOf(inputJson);
                if (sparkInput != null && !sparkInput.isBlank()) {
                    // Reuse the proven local trigger path so pipeline events narrate back here.
                    voiceSessionService.startPipelineFromConversation(session, sparkInput, repoUrl);
                    pipelineStarted = true;
                } else {
                    log.warn("start_pipeline tool_use missing sparkInput session={}", session.sessionId());
                }
            } else {
                // create_repo and other arbiter-internal tools are executed inside the arbiter; the
                // voice plane only acts on the terminal start_pipeline. Nothing to do here.
                log.debug("Ignoring non-terminal tool_use '{}' session={}", name, session.sessionId());
            }
        }

        @Override
        public void onDone() {
            String text = reply.toString().trim();
            // Always record the user's turn — even a tool-only turn (start_pipeline)
            // or an empty reply MUST stay in memory, or the next turn's history is
            // blind to what was just said/done and the brain contradicts itself.
            recordUserOnce();
            if (text.isEmpty()) {
                // No spoken reply. If the turn started a build, record that action so the
                // brain knows it already acted and doesn't re-ask for confirmation forever.
                if (pipelineStarted) {
                    session.appendHistory("assistant", "Starting the build pipeline now.", personaId);
                }
                // Settle the orb if nothing else took it (pipeline narration drives it otherwise).
                if (session.state() == VoiceState.THINKING) {
                    session.setState(VoiceState.IDLE);
                    session.outbound().sendControl(VoiceFrames.state(VoiceState.IDLE));
                }
                return;
            }
            // Finalize transcript, flip to speaking, and synthesize. The ElevenLabs
            // bridge streams audio out and returns the orb to idle on its own.
            session.outbound().sendControl(
                VoiceFrames.transcript("assistant", transcriptId, text, false));
            session.setState(VoiceState.SPEAKING);
            session.outbound().sendControl(VoiceFrames.state(VoiceState.SPEAKING));
            session.tts().speak(text);

            session.appendHistory("assistant", text, personaId);
        }

        /** Record the user utterance exactly once per turn (regardless of reply/tool path). */
        private void recordUserOnce() {
            if (!userRecorded) {
                userRecorded = true;
                session.appendHistory("user", userText);
            }
        }

        @Override
        public void onError(String userSafeMessage) {
            session.outbound().sendControl(VoiceFrames.error(
                userSafeMessage != null && !userSafeMessage.isBlank()
                    ? userSafeMessage : "Sorry — I hit a snag answering that. Try me again."));
            session.setState(VoiceState.IDLE);
            session.outbound().sendControl(VoiceFrames.state(VoiceState.IDLE));
        }
    }

    /** Extract {@code sparkInput} from a {@code start_pipeline} tool input JSON; null if absent. */
    private static String sparkInputOf(String inputJson) {
        return stringField(inputJson, "sparkInput", "spark_input");
    }

    /** Extract {@code repoUrl} from a {@code start_pipeline} tool input JSON; null if absent. */
    private static String repoUrlOf(String inputJson) {
        return stringField(inputJson, "repoUrl", "repo_url");
    }

    /** Read the first present, non-blank string field (camel or snake case) from a tool input JSON. */
    private static String stringField(String inputJson, String camel, String snake) {
        if (inputJson == null || inputJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = JSON.readTree(inputJson);
            JsonNode v = root.path(camel);
            if (v.isMissingNode() || v.isNull()) {
                v = root.path(snake);
            }
            String s = v.asString("");
            return s.isBlank() ? null : s;
        } catch (Exception e) {
            return null;
        }
    }
}
