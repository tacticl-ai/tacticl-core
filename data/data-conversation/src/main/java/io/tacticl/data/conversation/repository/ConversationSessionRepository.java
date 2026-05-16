package io.tacticl.data.conversation.repository;

import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.data.conversation.entity.SessionStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConversationSessionRepository extends MongoRepository<ConversationSession, String> {
    Optional<ConversationSession> findByIdAndUserId(String id, String userId);
    List<ConversationSession> findByUserIdOrderByUpdatedAtDesc(String userId);
    Optional<ConversationSession> findFirstByProjectIdAndUserIdAndStatusInOrderByUpdatedAtDesc(
            String projectId, String userId, Collection<SessionStatus> statuses);
    Optional<ConversationSession> findBySparkId(String sparkId);
}
