package io.tacticl.service.pipeline.dto;

import java.util.List;

public record RoleResultDto(
    String status,
    int iteration,
    double cost,
    List<RoleTaskDto> tasks
) {
    /**
     * One Planner-enumerated task for this role (Slice 3 task-plan passthrough).
     * {@code status} defaults to {@code "pending"}; the UI derives display state from the
     * ROLE's overall status until live per-task updates exist.
     */
    public record RoleTaskDto(String title, String status) {}
}
