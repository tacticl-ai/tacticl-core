package io.tacticl.data.pipeline.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Document("pipeline_checkpoints")
public class PipelineCheckpoint {

    @Id private String id;
    @Indexed private String pipelineRunId;
    @Indexed private String sparkId;
    private String phase;
    private String type;
    private String status;
    private Map<String, String> artifactPaths;
    private String hitlUrl;
    private String decision;
    private String feedback;
    private Instant createdAt;
    private Instant resolvedAt;

    protected PipelineCheckpoint() {}

    public static PipelineCheckpoint create(String pipelineRunId, String sparkId,
                                            String phase, String type,
                                            Map<String, String> artifactPaths) {
        PipelineCheckpoint cp = new PipelineCheckpoint();
        cp.id = UUID.randomUUID().toString();
        cp.pipelineRunId = pipelineRunId;
        cp.sparkId = sparkId;
        cp.phase = phase;
        cp.type = type;
        cp.status = "PENDING";
        cp.artifactPaths = artifactPaths;
        cp.createdAt = Instant.now();
        return cp;
    }

    public void resolve(CheckpointDecision decision, String feedback) {
        this.decision = decision.name();
        this.feedback = feedback;
        this.status = "RESOLVED";
        this.resolvedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getPipelineRunId() { return pipelineRunId; }
    public String getSparkId() { return sparkId; }
    public String getPhase() { return phase; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public Map<String, String> getArtifactPaths() { return artifactPaths; }
    public String getHitlUrl() { return hitlUrl; }
    public String getDecision() { return decision; }
    public String getFeedback() { return feedback; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }

    public void setId(String id) { this.id = id; }
    public void setPipelineRunId(String pipelineRunId) { this.pipelineRunId = pipelineRunId; }
    public void setSparkId(String sparkId) { this.sparkId = sparkId; }
    public void setPhase(String phase) { this.phase = phase; }
    public void setType(String type) { this.type = type; }
    public void setStatus(String status) { this.status = status; }
    public void setArtifactPaths(Map<String, String> artifactPaths) { this.artifactPaths = artifactPaths; }
    public void setHitlUrl(String hitlUrl) { this.hitlUrl = hitlUrl; }
    public void setDecision(String decision) { this.decision = decision; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
