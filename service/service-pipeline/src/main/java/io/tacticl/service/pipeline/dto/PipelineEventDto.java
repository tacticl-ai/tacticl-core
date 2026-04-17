package io.tacticl.service.pipeline.dto;

import io.tacticl.data.pipeline.entity.PipelineEvent;
import java.time.Instant;

public record PipelineEventDto(
    String id,
    String eventType,
    String role,
    String phase,
    Instant timestamp,
    String payloadJson
) {
    public static PipelineEventDto from(PipelineEvent event) {
        return new PipelineEventDto(
            event.getId(), event.getEventType(), event.getRole(),
            event.getPhase(), event.getTimestamp(), event.getPayloadJson()
        );
    }
}
