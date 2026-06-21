package io.tacticl.business.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Mutable per-connection state for one voice session. Created by
 * {@link VoiceSessionService#openSession} when the WS connects; retired on
 * disconnect. Holds the outbound sink, the STT/TTS bridges, the currently
 * narrated PDLC run, and the table of open checkpoints (so a spoken/clicked
 * decision can be mapped back to its spark).
 *
 * <p>All cross-thread fields are atomics / concurrent maps: STT callbacks, TTS
 * callbacks, and pipeline-event narration all touch a session from different
 * executor threads.
 */
public class VoiceSession {

    private final String sessionId;

    private final String userId;

    private final VoiceOutbound outbound;

    private final SttBridge stt;

    private final TtsBridge tts;

    /** The PDLC run this session is currently narrating (set on EXPLICIT_TRIGGER dispatch). */
    private final AtomicReference<String> activeRunId = new AtomicReference<>();

    /** The spark backing {@link #activeRunId} (needed to resolve checkpoint decisions). */
    private final AtomicReference<String> activeSparkId = new AtomicReference<>();

    private final AtomicReference<VoiceState> state = new AtomicReference<>(VoiceState.IDLE);

    /** Open checkpoints keyed by checkpointId → backing sparkId, for decision mapping. */
    private final Map<String, String> openCheckpoints = new ConcurrentHashMap<>();

    /** Text of the most recently narrated line — used to suppress consecutive duplicates. */
    private final AtomicReference<String> lastNarration = new AtomicReference<>();

    /**
     * Optional sink notified of each newly-appended turn, for durable write-through
     * (set by {@code VoiceSessionService} to persist into the conversation store).
     * Not fired by {@link #seedHistory} — rehydrated turns are already persisted.
     */
    private volatile Consumer<Utterance> historyListener;

    /**
     * Rolling conversational memory for this session, oldest-first. Provider-neutral
     * {@link Utterance}s so the session entity stays decoupled from any LLM client;
     * the conversation handler maps these onto its model's message type. Bounded to
     * the most recent {@link #MAX_HISTORY} utterances to cap prompt size / cost.
     */
    private final List<Utterance> history = Collections.synchronizedList(new ArrayList<>());

    /** Max retained utterances (~{@value} / 2 exchanges) — older turns roll off. */
    private static final int MAX_HISTORY = 40;

    public VoiceSession(String sessionId,
                        String userId,
                        VoiceOutbound outbound,
                        SttBridge stt,
                        TtsBridge tts) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.outbound = outbound;
        this.stt = stt;
        this.tts = tts;
    }

    public String sessionId() {
        return sessionId;
    }

    public String userId() {
        return userId;
    }

    public VoiceOutbound outbound() {
        return outbound;
    }

    public SttBridge stt() {
        return stt;
    }

    public TtsBridge tts() {
        return tts;
    }

    public String activeRunId() {
        return activeRunId.get();
    }

    public void bindRun(String runId, String sparkId) {
        activeRunId.set(runId);
        activeSparkId.set(sparkId);
    }

    public String activeSparkId() {
        return activeSparkId.get();
    }

    public VoiceState state() {
        return state.get();
    }

    public void setState(VoiceState newState) {
        state.set(newState);
    }

    /** Record an open checkpoint so a later decision frame can resolve its spark. */
    public void registerCheckpoint(String checkpointId, String sparkId) {
        if (checkpointId != null && sparkId != null) {
            openCheckpoints.put(checkpointId, sparkId);
        }
    }

    /** Resolve (and forget) the spark backing a checkpoint; null if unknown. */
    public String resolveCheckpointSpark(String checkpointId) {
        if (checkpointId == null) {
            return null;
        }
        String fromTable = openCheckpoints.remove(checkpointId);
        return fromTable != null ? fromTable : activeSparkId.get();
    }

    /**
     * Append a conversational turn to this session's rolling memory, trimming the
     * oldest entries past {@link #MAX_HISTORY}. No-op on blank text.
     *
     * @param role {@code "user"} or {@code "assistant"}
     */
    public void appendHistory(String role, String text) {
        appendHistory(role, text, null);
    }

    /**
     * Append a conversational turn carrying the producing persona id (for the
     * arbiter's sticky-persona routing). {@code personaId} may be null.
     *
     * @param role {@code "user"} or {@code "assistant"}
     */
    public void appendHistory(String role, String text, String personaId) {
        if (role == null || text == null || text.isBlank()) {
            return;
        }
        Utterance utterance = new Utterance(role, text, personaId);
        synchronized (history) {
            history.add(utterance);
            while (history.size() > MAX_HISTORY) {
                history.remove(0);
            }
        }
        Consumer<Utterance> listener = this.historyListener;
        if (listener != null) {
            listener.accept(utterance);
        }
    }

    /**
     * Seed rehydrated turns (from durable storage) into memory WITHOUT firing the
     * {@link #historyListener} — those turns are already persisted. Trims to the
     * most recent {@link #MAX_HISTORY}.
     */
    public void seedHistory(List<Utterance> prior) {
        if (prior == null || prior.isEmpty()) {
            return;
        }
        synchronized (history) {
            history.addAll(prior);
            while (history.size() > MAX_HISTORY) {
                history.remove(0);
            }
        }
    }

    /** Register the durable write-through sink for newly-appended turns. */
    public void setHistoryListener(Consumer<Utterance> listener) {
        this.historyListener = listener;
    }

    /** An immutable snapshot of this session's conversation memory, oldest-first. */
    public List<Utterance> history() {
        synchronized (history) {
            return List.copyOf(history);
        }
    }

    /**
     * Records {@code text} as the most recent narration and returns true only if it
     * differs from the immediately previous one. Lets a narration channel skip
     * re-rendering / re-speaking a line identical to the one just played (e.g. a
     * role-retry storm re-emitting the same {@code ROLE_STARTED}).
     */
    public boolean markNarration(String text) {
        return !java.util.Objects.equals(text, lastNarration.getAndSet(text));
    }

    /** One conversational turn: who spoke, what they said, and (optionally) which persona. */
    public record Utterance(String role, String text, String personaId) {

        /** Persona-less turn (user turns, in-JVM fallback replies). */
        public Utterance(String role, String text) {
            this(role, text, null);
        }
    }
}
