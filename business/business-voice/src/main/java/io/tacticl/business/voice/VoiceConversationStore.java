package io.tacticl.business.voice;

import io.tacticl.data.cloudorchestrator.entity.SessionMode;
import io.tacticl.data.cloudorchestrator.entity.Turn;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.data.conversation.repository.ConversationSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Durable persistence for voice conversations. Writes turns into the new-model
 * {@code conversation_sessions} collection ({@link ConversationSession#getTurns()}),
 * bypassing the deprecated {@code ConversationService} state machine entirely. The
 * conversation id doubles as the voice session id, so a client can resume a prior
 * conversation by reconnecting with {@code ?cid=<id>} and a picker can list/open
 * past conversations.
 *
 * <p>Best-effort: a persistence failure must never break a live turn — every method
 * contains its own faults and degrades to in-memory-only behaviour.
 */
@Service
@ConditionalOnProperty(name = "tacticl.voice.enabled", havingValue = "true")
public class VoiceConversationStore {

    private static final Logger log = LoggerFactory.getLogger(VoiceConversationStore.class);

    private static final String MODALITY_VOICE = "voice";

    private final ConversationSessionRepository repository;

    public VoiceConversationStore(ConversationSessionRepository repository) {
        this.repository = repository;
    }

    /**
     * Resolve the conversation a freshly-connected voice session attaches to.
     * Reuses an existing conversation the user owns when {@code requestedId} matches
     * one; otherwise creates a fresh conversation. Returns the resolved
     * conversation, or {@link Optional#empty()} if persistence is unavailable (the
     * caller then falls back to an ephemeral in-memory session).
     */
    public Optional<ConversationSession> resolveConversation(String userId, String requestedId) {
        try {
            if (requestedId != null && !requestedId.isBlank()) {
                Optional<ConversationSession> existing = repository.findByIdAndUserId(requestedId, userId);
                if (existing.isPresent()) {
                    return existing;
                }
                // Requested an id we don't own / that doesn't exist — start fresh rather
                // than risk colliding with another user's document on the primary key.
                log.debug("Voice conversation {} not found for user {} — creating a new one", requestedId, userId);
            }
            ConversationSession created = ConversationSession.create(userId, null);
            created.changeMode(SessionMode.VOICE_PTT);
            return Optional.of(repository.save(created));
        } catch (Exception e) {
            log.warn("Voice conversation resolve failed (user={}, id={}): {}", userId, requestedId, e.toString());
            return Optional.empty();
        }
    }

    /**
     * Append one turn to the conversation's durable transcript. The first user turn
     * also seeds the (otherwise null) title. No-op on blank text or unknown id.
     */
    public void appendTurn(String conversationId, String userId, String role, String text, String personaId) {
        if (conversationId == null || role == null || text == null || text.isBlank()) {
            return;
        }
        try {
            Optional<ConversationSession> opt = repository.findByIdAndUserId(conversationId, userId);
            if (opt.isEmpty()) {
                return;
            }
            ConversationSession session = opt.get();
            Turn turn = "assistant".equals(role)
                ? Turn.assistant(personaId, text, MODALITY_VOICE)
                : Turn.user(text, MODALITY_VOICE);
            session.appendTurn(turn);
            if ((session.getTitle() == null || session.getTitle().isBlank()) && "user".equals(role)) {
                session.setTitle(deriveTitle(text));
            }
            repository.save(session);
        } catch (Exception e) {
            log.warn("Voice conversation append failed (id={}): {}", conversationId, e.toString());
        }
    }

    /** A user's conversations as picker summaries, most-recently-updated first. */
    public List<ConversationSummary> listSummaries(String userId) {
        try {
            return repository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(c -> new ConversationSummary(c.getId(), titleOr(c), c.getUpdatedAt()))
                .toList();
        } catch (Exception e) {
            log.warn("Voice conversation list failed (user={}): {}", userId, e.toString());
            return List.of();
        }
    }

    /** One conversation the user owns, as a transcript for the picker to render. */
    public Optional<ConversationTranscript> transcript(String conversationId, String userId) {
        try {
            return repository.findByIdAndUserId(conversationId, userId).map(c -> {
                List<Turn> turns = c.getTurns() == null ? List.of() : c.getTurns();
                List<TranscriptTurn> mapped = turns.stream()
                    .filter(t -> t.getText() != null && !t.getText().isBlank())
                    .map(t -> new TranscriptTurn(t.getRole(), t.getText(), t.getPersonaId(), t.getTimestamp()))
                    .toList();
                return new ConversationTranscript(c.getId(), titleOr(c), mapped);
            });
        } catch (Exception e) {
            log.warn("Voice conversation get failed (id={}): {}", conversationId, e.toString());
            return Optional.empty();
        }
    }

    private static String titleOr(ConversationSession c) {
        return (c.getTitle() == null || c.getTitle().isBlank()) ? "New conversation" : c.getTitle();
    }

    private static String deriveTitle(String firstUserText) {
        String t = firstUserText.strip();
        return t.length() > 57 ? t.substring(0, 57) + "..." : t;
    }

    /** Picker row: a conversation's id, title, and last-updated time. */
    public record ConversationSummary(String id, String title, Instant updatedAt) {
    }

    /** Full conversation transcript for the picker to render on open. */
    public record ConversationTranscript(String id, String title, List<TranscriptTurn> turns) {
    }

    /** One rendered transcript turn. */
    public record TranscriptTurn(String role, String text, String personaId, Instant timestamp) {
    }
}
