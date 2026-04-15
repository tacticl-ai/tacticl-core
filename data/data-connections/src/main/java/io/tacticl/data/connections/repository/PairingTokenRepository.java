package io.tacticl.data.connections.repository;

import io.tacticl.data.connections.entity.PairingToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Optional;

public interface PairingTokenRepository extends MongoRepository<PairingToken, String> {
    Optional<PairingToken> findByTokenAndUsedFalseAndExpiresAtAfter(String token, Instant now);
}
