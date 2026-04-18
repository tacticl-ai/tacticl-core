package io.tacticl.service.pipeline.dto;

import io.tacticl.data.pipeline.entity.PipelineEvent;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

public record PipelineEventDto(
    String id,
    String pipelineRunId,
    String eventType,
    String role,
    Instant timestamp,
    Map<String, Object> metadata
) {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public static PipelineEventDto from(PipelineEvent e) {
        Map<String, Object> metadata = parsePayload(e.getPayloadJson());
        return new PipelineEventDto(
            e.getId(), e.getPipelineRunId(), e.getEventType(),
            e.getRole(), e.getTimestamp(), metadata
        );
    }

    private static Map<String, Object> parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) return Collections.emptyMap();
        try {
            return MAPPER.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }
}
