package io.tacticl.data.sparks.repository;

import io.tacticl.data.sparks.entity.Checkpoint;
import io.tacticl.data.sparks.entity.CheckpointStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface CheckpointRepository extends MongoRepository<Checkpoint, String> {
    List<Checkpoint> findBySparkIdAndUserId(String sparkId, String userId);
    Optional<Checkpoint> findByIdAndSparkIdAndUserId(String id, String sparkId, String userId);
    List<Checkpoint> findBySparkIdAndStatus(String sparkId, CheckpointStatus status);
    List<Checkpoint> findByUserId(String userId);
    Optional<Checkpoint> findByIdAndUserId(String id, String userId);
}
