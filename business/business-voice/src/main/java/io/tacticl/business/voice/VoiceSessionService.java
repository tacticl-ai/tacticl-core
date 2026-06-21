package io.tacticl.business.voice;

import io.cidadel.framework.exception.CidadelException;
import io.tacticl.business.pipeline.ingress.ChannelType;
import io.tacticl.business.pipeline.ingress.CheckpointDecisionPayload;
import io.tacticl.business.pipeline.ingress.IngressDispatchService;
import io.tacticl.business.pipeline.ingress.IngressKind;
import io.tacticl.business.pipeline.ingress.IngressRequest;
import io.tacticl.business.pipeline.ingress.RunOrigin;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.data.pipeline.entity.CheckpointDecision;
import io.tacticl.data.pipeline.entity.PipelineRun;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Per-session turn orchestration for the voice command center. This is the
 * transport-neutral brain behind the WebSocket: the WS handler in service-voice
 * owns the socket and feeds raw mic PCM and control intents in; this service
 * runs the STT → ingress → narration turn loop and drives the sphere/HUD state
 * frames back out through each session's {@link VoiceOutbound} sink.
 *
 * <p>Turn lifecycle:
 * <ol>
 *   <li>{@code start} → {@link #startTurn} opens STT, state → listening.</li>
 *   <li>mic PCM → {@link #pushAudio} forwards to Deepgram.</li>
 *   <li>interim transcripts → user {@code transcript} frames (partial:true).</li>
 *   <li>final transcript → user {@code transcript} frame (partial:false), state
 *       → thinking, classify CONVERSATION_TURN vs EXPLICIT_TRIGGER, dispatch via
 *       {@link IngressDispatchService}; an EXPLICIT_TRIGGER binds the returned
 *       {@code PipelineRun} so {@link VoiceRunUpdateChannel} can narrate it.</li>
 *   <li>{@code decision} → {@link #submitDecision} maps the protocol decision to
 *       the backend verb and resolves the checkpoint.</li>
 *   <li>{@code barge_in} → {@link #bargeIn} stops TTS and re-listens.</li>
 * </ol>
 */
@Service
@ConditionalOnProperty(name = "tacticl.voice.enabled", havingValue = "true")
public class VoiceSessionService {

    private static final Logger log = LoggerFactory.getLogger(VoiceSessionService.class);

    private static final String MODULE_NAME = "business-voice";

    /**
     * Routing key class for a voice session's EntryPoint resolution. Voice has a
     * single default EntryPoint (seeded with this key + isDefaultForChannel), so
     * every session resolves the same product via the lookup-only registry path.
     */
    static final String VOICE_EXTERNAL_KEY = "voice-default";

    /**
     * Lowercased lead tokens that promote a turn to an EXPLICIT_TRIGGER (kick off
     * a PDLC run) rather than a conversational turn. Deliberately conservative —
     * an ambiguous turn stays a CONVERSATION_TURN, which is the safe default
     * (admin gate only applies to triggers).
     */
    private static final List<String> BUILD_INTENT_PREFIXES = List.of(
        "build ", "create ", "implement ", "fix ", "add ", "refactor ",
        "ship ", "generate ", "write ", "deploy ", "make ", "send to pdlc");

    private final SttBridgeFactory sttFactory;

    private final TtsBridgeFactory ttsFactory;

    private final IngressDispatchService ingressDispatchService;

    private final VoiceSessionRegistry registry;

    private final VoiceConversationStore conversationStore;

    private final String voiceId;

    public VoiceSessionService(SttBridgeFactory sttFactory,
                               TtsBridgeFactory ttsFactory,
                               IngressDispatchService ingressDispatchService,
                               VoiceSessionRegistry registry,
                               VoiceConversationStore conversationStore,
                               VoiceProperties properties) {
        this.sttFactory = sttFactory;
        this.ttsFactory = ttsFactory;
        this.ingressDispatchService = ingressDispatchService;
        this.registry = registry;
        this.conversationStore = conversationStore;
        this.voiceId = properties.getVoiceId();
    }

    // ---------------------------------------------------------------------
    // Session lifecycle
    // ---------------------------------------------------------------------

    /**
     * Create and register a session for a freshly-connected WS. Builds the
     * STT/TTS bridges and wires the TTS audio path to the outbound sink.
     *
     * @param userId   the resolved (validated) tacticl user id for the connection
     * @param outbound the transport sink the WS handler implements
     */
    public VoiceSession openSession(String userId, VoiceOutbound outbound) {
        return openSession(userId, null, outbound);
    }

    /**
     * Create and register a session bound to a durable conversation. When
     * {@code requestedConversationId} names a conversation the user owns, that
     * conversation is resumed (its prior turns rehydrate the brain's memory and its
     * id is reused as the session id); otherwise a fresh conversation is created. The
     * resolved id + title are sent to the client so it can resume later and list the
     * conversation in the picker. New turns are written through to durable storage.
     *
     * @param userId                 the resolved (validated) tacticl user id
     * @param requestedConversationId the conversation to resume, or null for a new one
     * @param outbound               the transport sink the WS handler implements
     */
    public VoiceSession openSession(String userId, String requestedConversationId, VoiceOutbound outbound) {
        Optional<ConversationSession> convo = conversationStore.resolveConversation(userId, requestedConversationId);
        String sessionId = convo.map(ConversationSession::getId).orElseGet(() -> UUID.randomUUID().toString());
        SttBridge stt = sttFactory.create();
        TtsBridge tts = ttsFactory.create(voiceId);
        VoiceSession session = new VoiceSession(sessionId, userId, outbound, stt, tts);

        // Resume: rehydrate prior turns into memory (brain context) and wire durable
        // write-through so every new turn is persisted to the conversation store.
        convo.ifPresent(c -> {
            session.seedHistory(toUtterances(c.getTurns()));
            session.setHistoryListener(u ->
                conversationStore.appendTurn(sessionId, userId, u.role(), u.text(), u.personaId()));
        });

        // TTS audio chunks stream straight down as BINARY frames; envelope + state
        // are driven by the turn loop. The done handler returns the orb to idle.
        tts.onAudioChunk(outbound::sendAudio);
        tts.onDone(() -> transition(session, VoiceState.IDLE));
        tts.onError(t -> emitError(session, "tts", t));

        // STT callbacks: partials patch the user transcript; final triggers dispatch.
        AtomicReference<String> turnId = new AtomicReference<>(newTurnId());
        stt.onSpeechStarted(() -> transition(session, VoiceState.LISTENING));
        stt.onPartial(text ->
            session.outbound().sendControl(VoiceFrames.transcript("user", turnId.get(), text, true)));
        stt.onFinal(text -> {
            session.outbound().sendControl(VoiceFrames.transcript("user", turnId.get(), text, false));
            turnId.set(newTurnId());
            onFinalTranscript(session, text);
        });
        stt.onError(t -> emitError(session, "stt", t));

        registry.register(session);
        // Tell the client which conversation this is — for a brand-new conversation this
        // is how it learns the server-assigned id to resume later and show in the picker.
        convo.ifPresent(c -> outbound.sendControl(VoiceFrames.conversation(c.getId(), c.getTitle())));
        log.info("Voice session opened session={} user={} resumedTurns={}", sessionId, userId,
            convo.map(c -> c.getTurns() == null ? 0 : c.getTurns().size()).orElse(0));
        return session;
    }

    /** Map durable conversation turns onto the session's provider-neutral memory. */
    private static List<VoiceSession.Utterance> toUtterances(List<io.tacticl.data.cloudorchestrator.entity.Turn> turns) {
        if (turns == null || turns.isEmpty()) {
            return List.of();
        }
        return turns.stream()
            .filter(t -> t.getText() != null && !t.getText().isBlank())
            .map(t -> new VoiceSession.Utterance(t.getRole(), t.getText(), t.getPersonaId()))
            .toList();
    }

    /** Open the STT leg and flip the orb to listening (handles a {@code start} frame). */
    public void startTurn(VoiceSession session) {
        if (session == null) {
            return;
        }
        log.info("Voice startTurn — opening STT session={}", session.sessionId());
        // A new turn supersedes any in-flight narration.
        session.tts().stop();
        session.stt().open();
        transition(session, VoiceState.LISTENING);
    }

    /** Forward a mic PCM chunk to the STT leg (handles a BINARY UP frame). */
    public void pushAudio(VoiceSession session, byte[] pcmChunk) {
        if (session == null) {
            return;
        }
        // Audio arriving is itself proof the operator is speaking. If a {start}
        // control frame was missed or raced to a different socket, the STT leg
        // would be closed and every chunk dropped — so open it lazily on first
        // audio. open() is idempotent, so this fires once per turn then no-ops.
        if (!session.stt().isOpen()) {
            log.info("Mic audio with STT closed — lazily opening STT session={}", session.sessionId());
            startTurn(session);
        }
        session.stt().sendAudio(pcmChunk);
    }

    /** Stop capture (handles a {@code stop} frame). Final transcript arrives async via STT. */
    public void stopTurn(VoiceSession session) {
        if (session != null) {
            log.info("Voice stopTurn session={}", session.sessionId());
            session.stt().close();
        }
    }

    /**
     * Handle a typed command (a {@code text} control frame from the HUD composer — the
     * operator typed instead of speaking). The client already renders the user's typed
     * turn locally (it owns the operator turn), so we do NOT echo a user transcript here.
     * A new typed turn supersedes any in-flight narration, then it routes exactly like a
     * finalized spoken transcript: classify → dispatch (conversation turn or explicit
     * trigger) → narrate the reply back (transcript + TTS).
     */
    public void handleTypedText(VoiceSession session, String text) {
        if (session == null || text == null || text.isBlank()) {
            return;
        }
        log.info("Voice typed text session={} ({} chars)", session.sessionId(), text.length());
        // Supersede any in-flight reply, mirroring a fresh spoken turn / the client's tts.flush().
        session.tts().stop();
        onFinalTranscript(session, text);
    }

    /** Tear down a session on WS disconnect. */
    public void closeSession(VoiceSession session) {
        if (session == null) {
            return;
        }
        session.stt().close();
        session.tts().stop();
        registry.remove(session.sessionId());
        log.info("Voice session closed session={}", session.sessionId());
    }

    // ---------------------------------------------------------------------
    // Turn dispatch
    // ---------------------------------------------------------------------

    /**
     * The trigger point: a finalized utterance. Classifies intent, builds the
     * transport-neutral {@link IngressRequest}, and dispatches it. EXPLICIT_TRIGGER
     * binds the returned run so pipeline events narrate back to this session.
     */
    void onFinalTranscript(VoiceSession session, String transcript) {
        if (transcript == null || transcript.isBlank()) {
            throw new CidadelException(VoiceErrorDetails.EMPTY_TRANSCRIPT, MODULE_NAME, session.sessionId());
        }
        transition(session, VoiceState.THINKING);
        IngressKind kind = classify(transcript);
        try {
            // A spoken "build …" turn carries no provisioned repo (the analyst's create_repo
            // path supplies one via start_pipeline); the EntryPoint's repo is used as fallback.
            dispatchAndBind(session, kind, transcript, /* repoUrl */ null);
            // CONVERSATION_TURN: a wired ConversationTurnHandler narrates synchronously
            // within dispatch — it drives THINKING→SPEAKING and the TTS onDone settles
            // back to IDLE on its own. Only settle the orb ourselves if nothing took
            // over (handler absent or empty reply) — i.e. we're still in THINKING — so
            // we never clobber an in-flight narration.
            if (kind == IngressKind.CONVERSATION_TURN && session.state() == VoiceState.THINKING) {
                transition(session, VoiceState.IDLE);
            }
        } catch (CidadelException e) {
            // Authorization / linkage failures are expected for unauthorized callers —
            // surface them as a clean error frame rather than tearing down the socket.
            emitError(session, "dispatch", e);
            transition(session, VoiceState.IDLE);
        }
    }

    /**
     * Start a PDLC pipeline the conversation persona decided to kick off (its
     * {@code start_pipeline} skill — see {@code VoiceConversationTurnHandler}).
     * Routes as an EXPLICIT_TRIGGER through the SAME ingress + run-binding path as a
     * spoken "build …" turn, so pipeline events narrate back into this session
     * unchanged. Authorization/entry-point failures surface as a clean error frame.
     */
    public void startPipelineFromConversation(VoiceSession session, String sparkInput, String repoUrl) {
        if (session == null || sparkInput == null || sparkInput.isBlank()) {
            return;
        }
        try {
            dispatchAndBind(session, IngressKind.EXPLICIT_TRIGGER, sparkInput, repoUrl);
        } catch (CidadelException e) {
            emitError(session, "start_pipeline", e);
        }
    }

    /**
     * Dispatch an ingress turn and, when it yields a pipeline run, bind that run to
     * this session (both registry indexes) and announce it with a {@code submitted}
     * HUD frame so {@link VoiceRunUpdateChannel} narrates progress back here.
     */
    private Optional<PipelineRun> dispatchAndBind(VoiceSession session, IngressKind kind, String text,
                                                  String repoUrl) {
        IngressRequest request = new IngressRequest(
            originFor(session),
            session.userId(),
            kind,
            text,
            List.of(),
            /* productHint */ null,
            /* correlationId */ session.sessionId(),
            /* decision */ null,
            /* repoUrl */ repoUrl);
        Optional<PipelineRun> run = ingressDispatchService.dispatch(request);
        run.ifPresent(r -> {
            session.bindRun(r.getId(), r.getSparkId());
            registry.bindRun(r.getId(), session);
            session.outbound().sendControl(
                VoiceFrames.hud(null, "submitted", r.getId(), "Pipeline submitted"));
        });
        return run;
    }

    /**
     * Resolve an open checkpoint from a spoken/clicked decision (handles a
     * {@code decision} frame). Maps the protocol decision token
     * (APPROVE/CHANGES/REJECT) to the backend verb (APPROVED/REWORK/CANCEL).
     */
    public void submitDecision(VoiceSession session, String checkpointId, String decisionToken, String feedback) {
        if (session == null) {
            return;
        }
        String sparkId = session.resolveCheckpointSpark(checkpointId);
        if (sparkId == null) {
            throw new CidadelException(VoiceErrorDetails.UNRESOLVABLE_DECISION, MODULE_NAME, checkpointId);
        }
        CheckpointDecision decision = mapDecision(decisionToken);
        IngressKind kind = decision == CheckpointDecision.CANCEL
            ? IngressKind.CANCEL_RUN : IngressKind.CHECKPOINT_DECISION;
        IngressRequest request = new IngressRequest(
            originFor(session),
            session.userId(),
            kind,
            /* text */ null,
            List.of(),
            null,
            session.sessionId(),
            new CheckpointDecisionPayload(sparkId, checkpointId, decision, feedback));
        try {
            ingressDispatchService.dispatch(request);
            transition(session, VoiceState.THINKING);
        } catch (CidadelException e) {
            emitError(session, "decision", e);
        }
    }

    /** Barge-in (handles a {@code barge_in} frame): stop TTS, re-listen. */
    public void bargeIn(VoiceSession session) {
        if (session == null) {
            return;
        }
        session.tts().stop();
        transition(session, VoiceState.LISTENING);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Classify a turn. A leading build/fix verb promotes to EXPLICIT_TRIGGER (kick
     * off a PDLC run); everything else is a CONVERSATION_TURN. Conservative by
     * design — the admin gate on triggers means a mis-classified conversational
     * turn is harmless, while a mis-classified trigger from a non-admin is rejected.
     */
    IngressKind classify(String transcript) {
        String lower = transcript.trim().toLowerCase(Locale.ROOT);
        for (String prefix : BUILD_INTENT_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return IngressKind.EXPLICIT_TRIGGER;
            }
        }
        return IngressKind.CONVERSATION_TURN;
    }

    /** Map the protocol decision token onto the backend {@link CheckpointDecision} verb. */
    static CheckpointDecision mapDecision(String token) {
        if (token == null) {
            return CheckpointDecision.REWORK;
        }
        return switch (token.trim().toUpperCase(Locale.ROOT)) {
            case "APPROVE", "APPROVED" -> CheckpointDecision.APPROVED;
            case "CHANGES", "REWORK", "REQUEST_CHANGES" -> CheckpointDecision.REWORK;
            case "REJECT", "CANCEL" -> CheckpointDecision.CANCEL;
            default -> CheckpointDecision.REWORK;
        };
    }

    private RunOrigin originFor(VoiceSession session) {
        // externalKey resolves the (single, default) VOICE EntryPoint; destinationHandle
        // is the sessionId so any future per-session addressing has a stable target.
        return new RunOrigin(ChannelType.VOICE, VOICE_EXTERNAL_KEY, session.sessionId(), null);
    }

    private void transition(VoiceSession session, VoiceState state) {
        session.setState(state);
        session.outbound().sendControl(VoiceFrames.state(state));
    }

    private void emitError(VoiceSession session, String leg, Throwable t) {
        log.warn("Voice {} error session={}: {}", leg, session.sessionId(), t.getMessage(), t);
        session.outbound().sendControl(VoiceFrames.error(t.getMessage() != null ? t.getMessage() : leg + " error"));
    }

    private static String newTurnId() {
        return "t-" + UUID.randomUUID();
    }
}
