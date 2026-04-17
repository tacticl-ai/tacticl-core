package io.tacticl.data.conversation.repository;

import io.tacticl.data.conversation.entity.ConversationSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationSessionRepository extends MongoRepository<ConversationSession, String> {
    Optional<ConversationSession> findByIdAndUserId(String id, String userId);
    List<ConversationSession> findByUserIdOrderByUpdatedAtDesc(String userId);
}
