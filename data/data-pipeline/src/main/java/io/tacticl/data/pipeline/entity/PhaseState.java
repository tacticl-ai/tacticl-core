package io.tacticl.data.pipeline.entity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class PhaseState {
    private String status;
    private Instant startedAt;
    private Instant completedAt;
    private Map<String, RoleState> roles;
    private String checkpointId;
    private String checkpointStatus;

    protected PhaseState() {}

    public static PhaseState pending() {
        PhaseState ps = new PhaseState();
        ps.status = "PENDING";
        ps.roles = new HashMap<>();
        return ps;
    }

    public void markRunning() { this.status = "RUNNING"; this.startedAt = Instant.now(); }
    public void markCompleted() { this.status = "COMPLETED"; this.completedAt = Instant.now(); }
    public void markFailed() { this.status = "FAILED"; this.completedAt = Instant.now(); }
    public void setCheckpoint(String checkpointId) {
        this.checkpointId = checkpointId;
        this.checkpointStatus = "PENDING";
    }
    public void resolveCheckpoint(String decision) { this.checkpointStatus = decision; }

    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Map<String, RoleState> getRoles() { return roles; }
    public String getCheckpointId() { return checkpointId; }
    public String getCheckpointStatus() { return checkpointStatus; }
    public void setStatus(String status) { this.status = status; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public void setRoles(Map<String, RoleState> roles) { this.roles = roles; }
    public void setCheckpointId(String checkpointId) { this.checkpointId = checkpointId; }
    public void setCheckpointStatus(String checkpointStatus) { this.checkpointStatus = checkpointStatus; }
}
