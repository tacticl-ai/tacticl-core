package io.tacticl.service.pipeline.dto;

import io.tacticl.data.pipeline.entity.PdlcRole;
import io.tacticl.data.pipeline.entity.PhaseState;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.entity.RoleState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Lightweight projection of a {@link PipelineRun} for the Dashboard's pipeline list.
 *
 * <p>Intentionally omits the heavy per-role {@code roleResults} map carried by
 * {@link PipelineRunDto}; the list view only needs the headline fields plus the
 * activated-role names and the current role/checkpoint.
 */
public record PipelineRunSummaryDto(
    String id,
    String sparkId,
    String name,
    String playbook,
    String status,
    double totalCostUsd,
    String currentRole,
    List<String> activatedRoles,
    String currentCheckpointId,
    Instant createdAt,
    Instant updatedAt
) {
    private static final List<String> CANONICAL_ROLE_ORDER =
        Arrays.stream(PdlcRole.values()).map(Enum::name).toList();

    public static PipelineRunSummaryDto from(PipelineRun run) {
        List<String> activatedRoles = new ArrayList<>();
        String currentRole = null;

        if (run.getPhases() != null) {
            for (PhaseState phase : run.getPhases().values()) {
                if (phase.getRoles() != null) {
                    for (Map.Entry<String, RoleState> entry : phase.getRoles().entrySet()) {
                        String roleName = entry.getKey();
                        activatedRoles.add(roleName);
                        if ("RUNNING".equals(entry.getValue().getStatus())) {
                            currentRole = roleName;
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

        return new PipelineRunSummaryDto(
            run.getId(),
            run.getSparkId(),
            run.getName(),
            run.getPlaybook(),
            run.getStatus() != null ? run.getStatus().name() : null,
            run.getTotalCostUsd(),
            currentRole,
            Collections.unmodifiableList(activatedRoles),
            run.getCurrentCheckpointId(),
            run.getCreatedAt(),
            run.getUpdatedAt()
        );
    }
}
