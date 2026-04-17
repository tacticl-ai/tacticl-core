package io.tacticl.data.pipeline.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Document("pipeline_runs")
public class PipelineRun {

    @Id private String id;
    @Indexed private String userId;
    @Indexed private String sparkId;
    private String playbook;
    private PipelineStatus status;
    private String sparkRequest;
    private String repoUrl;
    private List<String> skipRoles;
    private double costCeilingUsd;
    private double totalCostUsd;
    private String arbiterPipelineId;
    private String currentCheckpointId;
    private String failureReason;
    private Map<String, PhaseState> phases;
    private Map<String, String> artifacts;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    protected PipelineRun() {}

    public static PipelineRun create(String userId, String sparkId, String sparkRequest,
                                     String repoUrl, String playbook, List<String> skipRoles,
                                     double costCeilingUsd) {
        PipelineRun run = new PipelineRun();
        run.id = UUID.randomUUID().toString();
        run.userId = userId;
        run.sparkId = sparkId;
        run.sparkRequest = sparkRequest;
        run.repoUrl = repoUrl;
        run.playbook = playbook;
        run.skipRoles = skipRoles;
        run.costCeilingUsd = costCeilingUsd;
        run.status = PipelineStatus.PENDING;
        run.totalCostUsd = 0.0;
        run.phases = new HashMap<>();
        run.artifacts = new HashMap<>();
        run.createdAt = Instant.now();
        run.updatedAt = run.createdAt;
        return run;
    }

    public void markRunning() {
        this.status = PipelineStatus.RUNNING;
        this.updatedAt = Instant.now();
    }

    public void markCompleted() {
        this.status = PipelineStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.updatedAt = this.completedAt;
    }

    public void markFailed(String reason) {
        this.status = PipelineStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = Instant.now();
        this.updatedAt = this.completedAt;
    }

    public void markCancelled() {
        this.status = PipelineStatus.CANCELLED;
        this.completedAt = Instant.now();
        this.updatedAt = this.completedAt;
    }

    public void pauseAtCheckpoint(String checkpointId, String phase) {
        this.status = PipelineStatus.PAUSED_AT_CHECKPOINT;
        this.currentCheckpointId = checkpointId;
        this.updatedAt = Instant.now();
        if (phase != null) {
            phases.computeIfAbsent(phase, k -> PhaseState.pending()).setCheckpoint(checkpointId);
        }
    }

    public void markRoleStarted(String phase, String role) {
        PhaseState phaseState = phases.computeIfAbsent(phase, k -> PhaseState.pending());
        if (!"RUNNING".equals(phaseState.getStatus())) phaseState.markRunning();
        phaseState.getRoles().computeIfAbsent(role, k -> RoleState.pending()).markRunning();
        this.updatedAt = Instant.now();
    }

    public void markRoleCompleted(String phase, String role, double costUsd) {
        PhaseState phaseState = phases.computeIfAbsent(phase, k -> PhaseState.pending());
        phaseState.getRoles().computeIfAbsent(role, k -> RoleState.pending()).markCompleted(costUsd);
        this.totalCostUsd += costUsd;
        this.updatedAt = Instant.now();
    }

    public void markRoleRework(String phase, String role) {
        PhaseState phaseState = phases.computeIfAbsent(phase, k -> PhaseState.pending());
        phaseState.getRoles().computeIfAbsent(role, k -> RoleState.pending()).incrementRework();
        this.updatedAt = Instant.now();
    }

    public void setArtifact(String key, String path) {
        artifacts.put(key, path);
        this.updatedAt = Instant.now();
    }

    public void resumeFromCheckpoint() {
        this.status = PipelineStatus.RUNNING;
        this.currentCheckpointId = null;
        this.updatedAt = Instant.now();
    }

    public void addCost(double cost) {
        this.totalCostUsd += cost;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getSparkId() { return sparkId; }
    public String getPlaybook() { return playbook; }
    public PipelineStatus getStatus() { return status; }
    public String getSparkRequest() { return sparkRequest; }
    public String getRepoUrl() { return repoUrl; }
    public List<String> getSkipRoles() { return skipRoles; }
    public double getCostCeilingUsd() { return costCeilingUsd; }
    public double getTotalCostUsd() { return totalCostUsd; }
    public String getArbiterPipelineId() { return arbiterPipelineId; }
    public String getCurrentCheckpointId() { return currentCheckpointId; }
    public String getFailureReason() { return failureReason; }
    public Map<String, PhaseState> getPhases() { return phases; }
    public Map<String, String> getArtifacts() { return artifacts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void setId(String id) { this.id = id; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setSparkId(String sparkId) { this.sparkId = sparkId; }
    public void setPlaybook(String playbook) { this.playbook = playbook; }
    public void setStatus(PipelineStatus status) { this.status = status; }
    public void setSparkRequest(String sparkRequest) { this.sparkRequest = sparkRequest; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public void setSkipRoles(List<String> skipRoles) { this.skipRoles = skipRoles; }
    public void setCostCeilingUsd(double costCeilingUsd) { this.costCeilingUsd = costCeilingUsd; }
    public void setTotalCostUsd(double totalCostUsd) { this.totalCostUsd = totalCostUsd; }
    public void setArbiterPipelineId(String arbiterPipelineId) { this.arbiterPipelineId = arbiterPipelineId; }
    public void setCurrentCheckpointId(String currentCheckpointId) { this.currentCheckpointId = currentCheckpointId; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public void setPhases(Map<String, PhaseState> phases) { this.phases = phases; }
    public void setArtifacts(Map<String, String> artifacts) { this.artifacts = artifacts; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
