package io.tacticl.service.pipeline.dto;

import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.entity.PipelineStatus;
import java.time.Instant;

public record PipelineRunDto(
    String id,
    String sparkId,
    String playbook,
    PipelineStatus status,
    double totalCostUsd,
    String currentCheckpointId,
    String failureReason,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt
) {
    public static PipelineRunDto from(PipelineRun run) {
        return new PipelineRunDto(
            run.getId(), run.getSparkId(), run.getPlaybook(),
            run.getStatus(), run.getTotalCostUsd(),
            run.getCurrentCheckpointId(), run.getFailureReason(),
            run.getCreatedAt(), run.getUpdatedAt(), run.getCompletedAt()
        );
    }
}
