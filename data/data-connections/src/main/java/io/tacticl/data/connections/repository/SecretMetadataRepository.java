package io.tacticl.data.connections.repository;

import io.tacticl.data.connections.entity.SecretMetadata;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SecretMetadataRepository extends MongoRepository<SecretMetadata, String> {

    List<SecretMetadata> findByUserId(String userId);

    Optional<SecretMetadata> findByIdAndUserId(String id, String userId);

    Optional<SecretMetadata> findByUserIdAndName(String userId, String name);

    void deleteByIdAndUserId(String id, String userId);
}
