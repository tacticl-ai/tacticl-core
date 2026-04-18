package io.tacticl.service.pipeline.dto;

import io.tacticl.data.pipeline.entity.PhaseState;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.entity.PipelineStatus;
import io.tacticl.data.pipeline.entity.RoleState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PipelineRunDto(
    String id,
    String sparkId,
    String playbook,
    PipelineStatus status,
    double totalCostUsd,
    String currentCheckpointId,
    String failureReason,
    List<String> activatedRoles,
    String currentRole,
    Map<String, RoleResultDto> roleResults,
    List<String> skippedRoles,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt
) {
    private static final List<String> CANONICAL_ROLE_ORDER = Arrays.asList(
        "PM", "RESEARCHER", "ARCHITECT", "DESIGNER", "PLANNER",
        "IMPLEMENTER", "REVIEWER", "TESTER", "SECURITY_ANALYST",
        "TECHNICAL_WRITER", "DEVOPS", "RETRO_ANALYST"
    );

    public static PipelineRunDto from(PipelineRun run) {
        List<String> activatedRoles = new ArrayList<>();
        String[] currentRoleHolder = {null};
        Map<String, RoleResultDto> roleResults = new LinkedHashMap<>();

        if (run.getPhases() != null) {
            for (PhaseState phase : run.getPhases().values()) {
                if (phase.getRoles() != null) {
                    for (Map.Entry<String, RoleState> entry : phase.getRoles().entrySet()) {
                        String roleName = entry.getKey();
                        RoleState roleState = entry.getValue();
                        activatedRoles.add(roleName);
                        String normalizedStatus = "RUNNING".equals(roleState.getStatus())
                            ? "EXECUTING" : roleState.getStatus();
                        roleResults.put(roleName, new RoleResultDto(
                            normalizedStatus,
                            roleState.getReworkCount() + 1,
                            roleState.getCostUsd()
                        ));
                        if ("RUNNING".equals(roleState.getStatus())) {
                            currentRoleHolder[0] = roleName;
                        }
                    }
                }
            }
        }

        activatedRoles.sort((a, b) -> {
            int ia = CANONICAL_ROLE_ORDER.indexOf(a);
            int ib = CANONICAL_ROLE_ORDER.indexOf(b);
            if (ia < 0) ia = CANONICAL_ROLE_ORDER.size();
            if (ib < 0) ib = CANONICAL_ROLE_ORDER.size();
            return Integer.compare(ia, ib);
        });

        return new PipelineRunDto(
            run.getId(), run.getSparkId(), run.getPlaybook(),
            run.getStatus(), run.getTotalCostUsd(),
            run.getCurrentCheckpointId(), run.getFailureReason(),
            Collections.unmodifiableList(activatedRoles),
            currentRoleHolder[0],
            Collections.unmodifiableMap(roleResults),
            run.getSkipRoles() != null ? run.getSkipRoles() : List.of(),
            run.getCreatedAt(), run.getUpdatedAt(), run.getCompletedAt()
        );
    }
}
