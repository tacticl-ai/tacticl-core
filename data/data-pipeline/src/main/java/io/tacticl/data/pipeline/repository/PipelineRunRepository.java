package io.tacticl.data.pipeline.repository;

import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.entity.PipelineStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface PipelineRunRepository extends MongoRepository<PipelineRun, String> {
    Optional<PipelineRun> findByIdAndUserId(String id, String userId);
    Optional<PipelineRun> findBySparkIdAndUserId(String sparkId, String userId);
    List<PipelineRun> findByUserIdOrderByCreatedAtDesc(String userId);
    List<PipelineRun> findByStatus(PipelineStatus status);
}
