package io.tacticl.data.pipeline.entity;

public class RoleState {
    private String status;
    private int reworkCount;
    private double costUsd;

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
    public void setStatus(String status) { this.status = status; }
    public void setReworkCount(int reworkCount) { this.reworkCount = reworkCount; }
    public void setCostUsd(double costUsd) { this.costUsd = costUsd; }
}
