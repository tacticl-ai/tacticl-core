package io.tacticl.data.pipeline.repository;

import io.tacticl.data.pipeline.entity.PipelineCheckpoint;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface PipelineCheckpointRepository extends MongoRepository<PipelineCheckpoint, String> {
    Optional<PipelineCheckpoint> findByIdAndPipelineRunId(String id, String pipelineRunId);
}
