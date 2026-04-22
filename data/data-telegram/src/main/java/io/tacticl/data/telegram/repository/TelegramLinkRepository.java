package io.tacticl.data.telegram.repository;

import io.tacticl.data.telegram.entity.TelegramLink;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TelegramLinkRepository extends MongoRepository<TelegramLink, String> {

    List<TelegramLink> findByUserIdAndIsActiveTrue(String userId);

    // Includes soft-deleted rows — used by redeemToken to detect prior links so
    // cross-user chat-id collisions are rejected and a user's own unlink can be re-activated.
    Optional<TelegramLink> findByChatId(long chatId);

    Optional<TelegramLink> findByChatIdAndIsActiveTrue(long chatId);

    Optional<TelegramLink> findByUserIdAndChatId(String userId, long chatId);
}
