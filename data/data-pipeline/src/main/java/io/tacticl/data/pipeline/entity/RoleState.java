package io.tacticl.data.pipeline.entity;

import java.util.List;

public class RoleState {
    private String status;
    private int reworkCount;
    private double costUsd;

    /**
     * Planner-enumerated tasks for this role (Slice 3 task-plan passthrough).
     * Nullable — most roles never receive a task plan; only the roles the Planner
     * plans for get a populated list. Each entry defaults to status {@code "pending"}.
     */
    private List<RoleTask> tasks;

    protected RoleState() {}

    public static RoleState pending() {
        RoleState rs = new RoleState();
        rs.status = "PENDING";
        rs.reworkCount = 0;
        rs.costUsd = 0.0;
        return rs;
    }

    public void markRunning() { this.status = "RUNNING"; }
    public void markCompleted(double cost) { this.status = "COMPLETED"; this.costUsd = cost; }
    public void markFailed() { this.status = "FAILED"; }
    public void markSkipped() { this.status = "SKIPPED"; }
    public void incrementRework() { this.reworkCount++; }

    public String getStatus() { return status; }
    public int getReworkCount() { return reworkCount; }
    public double getCostUsd() { return costUsd; }
    public List<RoleTask> getTasks() { return tasks; }
    public void setStatus(String status) { this.status = status; }
    public void setReworkCount(int reworkCount) { this.reworkCount = reworkCount; }
    public void setCostUsd(double costUsd) { this.costUsd = costUsd; }
    public void setTasks(List<RoleTask> tasks) { this.tasks = tasks; }
}
