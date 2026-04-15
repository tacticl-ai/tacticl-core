package io.tacticl.data.connections.repository;

import io.tacticl.data.connections.entity.Connection;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ConnectionRepository extends MongoRepository<Connection, String> {
    List<Connection> findByUserId(String userId);
    Optional<Connection> findByUserIdAndProvider(String userId, String provider);
    void deleteByUserIdAndProvider(String userId, String provider);
}
