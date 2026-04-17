package io.tacticl.data.pipeline.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.UUID;

@Document("pipeline_events")
public class PipelineEvent {

    @Id private String id;
    @Indexed private String pipelineRunId;
    private String eventType;
    private String role;
    private String phase;
    private Instant timestamp;
    private String payloadJson;

    protected PipelineEvent() {}

    public static PipelineEvent create(String pipelineRunId, String eventType,
                                       String role, String phase, String payloadJson) {
        PipelineEvent e = new PipelineEvent();
        e.id = UUID.randomUUID().toString();
        e.pipelineRunId = pipelineRunId;
        e.eventType = eventType;
        e.role = role;
        e.phase = phase;
        e.timestamp = Instant.now();
        e.payloadJson = payloadJson;
        return e;
    }

    public String getId() { return id; }
    public String getPipelineRunId() { return pipelineRunId; }
    public String getEventType() { return eventType; }
    public String getRole() { return role; }
    public String getPhase() { return phase; }
    public Instant getTimestamp() { return timestamp; }
    public String getPayloadJson() { return payloadJson; }

    public void setId(String id) { this.id = id; }
    public void setPipelineRunId(String pipelineRunId) { this.pipelineRunId = pipelineRunId; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setRole(String role) { this.role = role; }
    public void setPhase(String phase) { this.phase = phase; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
}
