package io.tacticl.data.pipeline.repository;

import io.tacticl.data.pipeline.entity.PipelineEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PipelineEventRepository extends MongoRepository<PipelineEvent, String> {
    Page<PipelineEvent> findByPipelineRunIdOrderByTimestampAsc(String pipelineRunId, Pageable pageable);
}
