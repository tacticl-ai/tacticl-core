package io.tacticl.data.telegram.repository;

import io.tacticl.data.telegram.entity.TelegramLinkToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TelegramLinkTokenRepository extends MongoRepository<TelegramLinkToken, String> {

    Optional<TelegramLinkToken> findByToken(String token);
}
