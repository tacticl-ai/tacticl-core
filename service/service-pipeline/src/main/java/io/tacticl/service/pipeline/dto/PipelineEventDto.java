package io.tacticl.service.pipeline.dto;

import io.tacticl.data.pipeline.entity.PipelineEvent;
import java.time.Instant;

public record PipelineEventDto(
    String id,
    String pipelineRunId,
    String eventType,
    String role,
    String phase,
    Instant timestamp,
    String payloadJson
) {
    public static PipelineEventDto from(PipelineEvent e) {
        return new PipelineEventDto(e.getId(), e.getPipelineRunId(), e.getEventType(),
                                    e.getRole(), e.getPhase(), e.getTimestamp(), e.getPayloadJson());
    }
}
