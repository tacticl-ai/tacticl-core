package io.tacticl.data.pipeline.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mongo projection of a Temporal-owned pipeline workflow (per SAD §9.2).
 *
 * <p>Primary query is user-scoped active work — backed by the
 * {@code userId_status_updatedAt} compound index.
 */
@Document("pipeline_runs")
@CompoundIndexes({
    // Primary query: "user's active work, most recent first" (SAD §9.2)
    @CompoundIndex(name = "userId_status_updatedAt",
                   def = "{'userId': 1, 'status': 1, 'updatedAt': -1}"),
    // Cleanup queries: 7d-old garbage detector
    @CompoundIndex(name = "status_updatedAt",
                   def = "{'status': 1, 'updatedAt': 1}")
})
public class PipelineRun {

    @Id private String id;
    @Indexed private String userId;
    @Indexed(unique = true) private String sparkId;

    /**
     * Temporal workflow id (e.g. {@code "pipeline-{sparkId}"}); routes all signals.
     * Nullable on legacy records created before the Temporal cut-over.
     */
    @Indexed(unique = true, sparse = true) private String workflowId;

    /**
     * Human-readable, ~30 chars. Auto-set at creation from the
     * {@code propose_implementation} summary. PM persona and UI refer to this pipeline
     * by name ("the auth endpoint") — the raw id is NEVER shown to the user.
     * Nullable for backward compat with pre-rollout records.
     */
    private String name;

    /**
     * Which session originated this pipeline. Used for transcript linkage / audit;
     * NOT used for routing or authorization — any of the user's sessions can operate
     * on this pipeline (per SAD §9.2).
     */
    private String creatingSessionId;

    private String playbook;
    private PipelineStatus status;
    private String sparkRequest;
    private String repoUrl;
    private List<String> skipRoles;
    private double costCeilingUsd;
    private double totalCostUsd;
    private String arbiterPipelineId;
    private String currentCheckpointId;

    /**
     * If status=BLOCKED, the id of the open checkpoint blocking the run
     * (per SAD §9.2). Distinct from {@link #currentCheckpointId} which is set
     * by the legacy {@link #pauseAtCheckpoint(String, String)} flow.
     */
    private String blockedCheckpointId;

    /**
     * Per-role persona version snapshot taken at run start (per SAD §4.5.2).
     * Enables re-running a past pipeline with the exact prompts that produced
     * the original artifacts even after personas are edited.
     */
    private Map<PdlcRole, Integer> personaVersions;

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
        run.personaVersions = new HashMap<>();
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
        this.blockedCheckpointId = null;
        this.updatedAt = Instant.now();
    }

    public void addCost(double cost) {
        this.totalCostUsd += cost;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getSparkId() { return sparkId; }
    public String getWorkflowId() { return workflowId; }
    public String getName() { return name; }
    public String getCreatingSessionId() { return creatingSessionId; }
    public String getPlaybook() { return playbook; }
    public PipelineStatus getStatus() { return status; }
    public String getSparkRequest() { return sparkRequest; }
    public String getRepoUrl() { return repoUrl; }
    public List<String> getSkipRoles() { return skipRoles; }
    public double getCostCeilingUsd() { return costCeilingUsd; }
    public double getTotalCostUsd() { return totalCostUsd; }
    public String getArbiterPipelineId() { return arbiterPipelineId; }
    public String getCurrentCheckpointId() { return currentCheckpointId; }
    public String getBlockedCheckpointId() { return blockedCheckpointId; }
    public Map<PdlcRole, Integer> getPersonaVersions() { return personaVersions; }
    public String getFailureReason() { return failureReason; }
    public Map<String, PhaseState> getPhases() { return phases; }
    public Map<String, String> getArtifacts() { return artifacts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void setId(String id) { this.id = id; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setSparkId(String sparkId) { this.sparkId = sparkId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public void setName(String name) { this.name = name; }
    public void setCreatingSessionId(String creatingSessionId) { this.creatingSessionId = creatingSessionId; }
    public void setPlaybook(String playbook) { this.playbook = playbook; }
    public void setStatus(PipelineStatus status) { this.status = status; }
    public void setSparkRequest(String sparkRequest) { this.sparkRequest = sparkRequest; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public void setSkipRoles(List<String> skipRoles) { this.skipRoles = skipRoles; }
    public void setCostCeilingUsd(double costCeilingUsd) { this.costCeilingUsd = costCeilingUsd; }
    public void setTotalCostUsd(double totalCostUsd) { this.totalCostUsd = totalCostUsd; }
    public void setArbiterPipelineId(String arbiterPipelineId) { this.arbiterPipelineId = arbiterPipelineId; }
    public void setCurrentCheckpointId(String currentCheckpointId) { this.currentCheckpointId = currentCheckpointId; }
    public void setBlockedCheckpointId(String blockedCheckpointId) { this.blockedCheckpointId = blockedCheckpointId; }
    public void setPersonaVersions(Map<PdlcRole, Integer> personaVersions) { this.personaVersions = personaVersions; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public void setPhases(Map<String, PhaseState> phases) { this.phases = phases; }
    public void setArtifacts(Map<String, String> artifacts) { this.artifacts = artifacts; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
