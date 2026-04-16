package io.tacticl.data.sparks.repository;

import io.tacticl.data.sparks.entity.Spark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface SparkRepository extends MongoRepository<Spark, String> {
    Page<Spark> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    Optional<Spark> findByIdAndUserId(String id, String userId);
}
