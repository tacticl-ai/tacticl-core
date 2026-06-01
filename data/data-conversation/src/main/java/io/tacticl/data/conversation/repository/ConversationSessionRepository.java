package io.tacticl.data.conversation.repository;

import io.tacticl.data.cloudorchestrator.entity.SessionStatus;
import io.tacticl.data.conversation.entity.ConversationSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConversationSessionRepository extends MongoRepository<ConversationSession, String> {

    Optional<ConversationSession> findByIdAndUserId(String id, String userId);

    /** Spec-required: list sessions for a user (per task brief). */
    List<ConversationSession> findByUserId(String userId);

    /** Spec-required: filter user sessions by status. */
    List<ConversationSession> findByUserIdAndStatus(String userId, SessionStatus status);

    /** UI sort variant — most recently updated first. */
    List<ConversationSession> findByUserIdOrderByUpdatedAtDesc(String userId);

    /** Telegram group lookup: most recent open session in a given project. */
    Optional<ConversationSession> findFirstByProjectIdAndUserIdAndStatusInOrderByUpdatedAtDesc(
            String projectId, String userId, Collection<SessionStatus> statuses);

    /** @deprecated Sparks are now tracked via {@code sessionStartedSparkIds}. */
    @Deprecated
    Optional<ConversationSession> findBySparkId(String sparkId);
}
