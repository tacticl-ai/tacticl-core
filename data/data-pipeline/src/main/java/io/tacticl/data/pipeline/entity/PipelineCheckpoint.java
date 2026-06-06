package io.tacticl.data.pipeline.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Pipeline checkpoint (per SAD §9.3).
 *
 * <p>The primary query is "what's waiting on the user" — backed by the
 * {@code userId_status_raisedAt} compound index. Optimistic locking on
 * {@code _id + status=OPEN} ensures only the first resolver wins (used by
 * the Telegram inline-button race + sibling-session race; per SAD §3.6).
 */
@Document("pipeline_checkpoints")
@CompoundIndexes({
    // Bootstrap query: "what's waiting on this user" (SAD §9.3)
    @CompoundIndex(name = "userId_status_raisedAt",
                   def = "{'userId': 1, 'status': 1, 'raisedAt': -1}")
})
public class PipelineCheckpoint {

    @Id private String id;
    @Indexed private String pipelineRunId;
    @Indexed private String sparkId;

    /**
     * Denormalized from {@link PipelineRun#getUserId()} for fast user-scoped queries
     * (per SAD §9.3). Nullable on legacy records written before this field existed.
     */
    @Indexed private String userId;

    private String phase;
    private String type;

    /**
     * Stored as String for backward compat with legacy "PENDING" / "RESOLVED" string
     * writes; new code should use {@link CheckpointStatus} values via
     * {@link #getStatusEnum()} / {@link #setStatusEnum(CheckpointStatus)}.
     */
    private String status;

    private Map<String, String> artifactPaths;
    private String hitlUrl;
    private String decision;
    private String feedback;

    /**
     * Temporal merge/interview gate correlation (from the arbiter's {@code blocked}
     * callback). Echoed back on resolution via SignalPipelineDecision so the workflow
     * validates the live ask (first-decision-wins, replay defense). Null for legacy/non-
     * Temporal checkpoints.
     */
    private String askId;
    private String gateNonce;

    /**
     * Session id that resolved this checkpoint (per SAD §9.3). Cross-session
     * checkpoints can be answered by any of the user's sessions.
     */
    private String resolvedBy;

    private Instant createdAt;
    private Instant resolvedAt;

    // Optimistic lock — double-tap race on Telegram inline buttons would otherwise allow
    // two concurrent decisions (e.g. APPROVE + REWORK) to both commit, leaving the pipeline
    // in an inconsistent state and double-calling the arbiter resume RPC.
    @Version private Long version;

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
        cp.status = CheckpointStatus.OPEN.name();
        cp.artifactPaths = artifactPaths;
        cp.createdAt = Instant.now();
        return cp;
    }

    public static PipelineCheckpoint create(String pipelineRunId, String sparkId, String userId,
                                            String phase, String type,
                                            Map<String, String> artifactPaths) {
        PipelineCheckpoint cp = create(pipelineRunId, sparkId, phase, type, artifactPaths);
        cp.userId = userId;
        return cp;
    }

    public void resolve(CheckpointDecision decision, String feedback) {
        this.decision = decision.name();
        this.feedback = feedback;
        this.status = CheckpointStatus.RESOLVED.name();
        this.resolvedAt = Instant.now();
    }

    public void resolve(CheckpointDecision decision, String feedback, String resolvedBy) {
        resolve(decision, feedback);
        this.resolvedBy = resolvedBy;
    }

    public String getId() { return id; }
    public String getPipelineRunId() { return pipelineRunId; }
    public String getSparkId() { return sparkId; }
    public String getUserId() { return userId; }
    public String getPhase() { return phase; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public CheckpointStatus getStatusEnum() {
        return status == null ? null : CheckpointStatus.valueOf(status);
    }
    public Map<String, String> getArtifactPaths() { return artifactPaths; }
    public String getHitlUrl() { return hitlUrl; }
    public String getAskId() { return askId; }
    public void setAskId(String askId) { this.askId = askId; }
    public String getGateNonce() { return gateNonce; }
    public void setGateNonce(String gateNonce) { this.gateNonce = gateNonce; }
    public String getDecision() { return decision; }
    public String getFeedback() { return feedback; }
    public String getResolvedBy() { return resolvedBy; }

    public Instant getCreatedAt() { return createdAt; }
    /** Alias for {@link #getCreatedAt()} — spec naming (SAD §9.3). */
    public Instant getRaisedAt() { return createdAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public Long getVersion() { return version; }

    public void setId(String id) { this.id = id; }
    public void setPipelineRunId(String pipelineRunId) { this.pipelineRunId = pipelineRunId; }
    public void setSparkId(String sparkId) { this.sparkId = sparkId; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setPhase(String phase) { this.phase = phase; }
    public void setType(String type) { this.type = type; }
    public void setStatus(String status) { this.status = status; }
    public void setStatusEnum(CheckpointStatus status) {
        this.status = status == null ? null : status.name();
    }
    public void setArtifactPaths(Map<String, String> artifactPaths) { this.artifactPaths = artifactPaths; }
    public void setHitlUrl(String hitlUrl) { this.hitlUrl = hitlUrl; }
    public void setDecision(String decision) { this.decision = decision; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setRaisedAt(Instant raisedAt) { this.createdAt = raisedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public void setVersion(Long version) { this.version = version; }
}
