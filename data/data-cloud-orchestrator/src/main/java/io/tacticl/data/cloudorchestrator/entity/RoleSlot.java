package io.tacticl.data.cloudorchestrator.entity;

/**
 * A single slot inside a {@link PhaseConfig} (per SAD §8.1).
 *
 * <p>{@link #personaId} references {@link Persona#getId()} (a PDLC-family persona).
 * The slot can be {@code optional} (skipped without failing the phase) and has a
 * {@code reworkBudget} (max rework iterations enforced by the pipeline workflow).
 */
public class RoleSlot {

    private String personaId;       // references personas.id (PDLC family)
    private boolean optional;
    private int reworkBudget;       // max iterations

    protected RoleSlot() {}

    public RoleSlot(String personaId, boolean optional, int reworkBudget) {
        this.personaId = personaId;
        this.optional = optional;
        this.reworkBudget = reworkBudget;
    }

    public String getPersonaId() { return personaId; }
    public boolean isOptional() { return optional; }
    public int getReworkBudget() { return reworkBudget; }

    public void setPersonaId(String personaId) { this.personaId = personaId; }
    public void setOptional(boolean optional) { this.optional = optional; }
    public void setReworkBudget(int reworkBudget) { this.reworkBudget = reworkBudget; }
}
