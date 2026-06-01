package io.tacticl.business.voice;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Thread-safe registry of live voice sessions, indexed two ways:
 * <ul>
 *   <li>{@code sessionId → VoiceSession} — the primary handle the WS transport
 *       owns for the lifetime of the connection.</li>
 *   <li>{@code runId → VoiceSession} — the reverse index the
 *       {@link VoiceRunUpdateChannel} uses to route a {@code PipelineCallbackEvent}
 *       back to the session that triggered it.</li>
 * </ul>
 *
 * <p>The {@code runId} binding is established when a turn dispatches an
 * EXPLICIT_TRIGGER and gets back a {@code PipelineRun}; it is soft-retired when
 * the run completes (so stray late events stop resolving) and hard-removed when
 * the session disconnects.
 */
@Component
@ConditionalOnProperty(name = "tacticl.voice.enabled", havingValue = "true")
public class VoiceSessionRegistry {

    private final ConcurrentMap<String, VoiceSession> bySession = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, VoiceSession> byRun = new ConcurrentHashMap<>();

    /** Register a freshly-opened session. */
    public void register(VoiceSession session) {
        bySession.put(session.sessionId(), session);
    }

    /** Bind a PDLC run to the session that triggered it (reverse index for narration). */
    public void bindRun(String runId, VoiceSession session) {
        if (runId == null || session == null) {
            return;
        }
        byRun.put(runId, session);
    }

    public Optional<VoiceSession> bySessionId(String sessionId) {
        return Optional.ofNullable(sessionId == null ? null : bySession.get(sessionId));
    }

    public Optional<VoiceSession> byRunId(String runId) {
        return Optional.ofNullable(runId == null ? null : byRun.get(runId));
    }

    /** Soft-retire a run binding (terminal pipeline event) without dropping the session. */
    public void retireRun(String runId) {
        if (runId != null) {
            byRun.remove(runId);
        }
    }

    /** Remove a session and any run bindings pointing at it (WS disconnect). */
    public void remove(String sessionId) {
        VoiceSession removed = bySession.remove(sessionId);
        if (removed != null) {
            byRun.values().removeIf(s -> s == removed);
        }
    }

    /** Live session count — visible for tests / metrics. */
    public int activeSessions() {
        return bySession.size();
    }

    /** Live run-binding count — visible for tests / metrics. */
    public int activeRunBindings() {
        return byRun.size();
    }
}
