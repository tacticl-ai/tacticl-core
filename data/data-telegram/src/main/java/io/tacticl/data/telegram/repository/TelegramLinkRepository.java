package io.tacticl.data.telegram.repository;

import io.tacticl.data.telegram.entity.TelegramLink;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TelegramLinkRepository extends MongoRepository<TelegramLink, String> {

    List<TelegramLink> findByUserIdAndIsActiveTrue(String userId);

    Optional<TelegramLink> findByChatId(long chatId);

    Optional<TelegramLink> findByUserIdAndChatId(String userId, long chatId);
}
