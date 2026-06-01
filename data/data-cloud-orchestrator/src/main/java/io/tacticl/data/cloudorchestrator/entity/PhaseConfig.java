package io.tacticl.data.cloudorchestrator.entity;

import io.tacticl.data.pipeline.entity.PdlcPhase;

import java.util.ArrayList;
import java.util.List;

/**
 * One phase of a {@link PlaybookV2} (per SAD §8.1).
 *
 * <p>Roles inside a phase may run {@code parallel} or sequentially.
 * If {@code checkpointAfter} is true, the pipeline workflow raises a checkpoint
 * at end of phase.
 */
public class PhaseConfig {

    private PdlcPhase phase;
    private List<RoleSlot> roles;
    private boolean parallel;           // roles run concurrently when true
    private boolean checkpointAfter;    // raise checkpoint at end of phase

    protected PhaseConfig() {}

    public PhaseConfig(PdlcPhase phase, List<RoleSlot> roles,
                       boolean parallel, boolean checkpointAfter) {
        this.phase = phase;
        this.roles = roles != null ? roles : new ArrayList<>();
        this.parallel = parallel;
        this.checkpointAfter = checkpointAfter;
    }

    public PdlcPhase getPhase() { return phase; }
    public List<RoleSlot> getRoles() { return roles; }
    public boolean isParallel() { return parallel; }
    public boolean isCheckpointAfter() { return checkpointAfter; }

    public void setPhase(PdlcPhase phase) { this.phase = phase; }
    public void setRoles(List<RoleSlot> roles) { this.roles = roles; }
    public void setParallel(boolean parallel) { this.parallel = parallel; }
    public void setCheckpointAfter(boolean checkpointAfter) { this.checkpointAfter = checkpointAfter; }
}
