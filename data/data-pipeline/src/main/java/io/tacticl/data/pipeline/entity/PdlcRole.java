package io.tacticl.data.pipeline.entity;

/**
 * PDLC role enum. Each value names a PDLC-family persona id (per SAD §4.3).
 *
 * <p><b>Rename note (2026-05-25 cloud-agent-orchestrator):</b> the legacy {@code PM}
 * value was renamed to {@code PO} (Product Owner) to align with the persona registry.
 * The migration runner bulk-updates {@code pipeline_runs.role} and
 * {@code pipeline_events.role} Mongo records from {@code "PM"} to {@code "PO"} in-place,
 * so no {@code @JsonAlias} is needed (single-cut deploy, per SAD §4.3).
 *
 * <p>The chat-level {@code market-researcher} persona is distinct from {@link #RESEARCHER}
 * (the technical/feasibility researcher) and lives outside this enum — see the personas
 * collection for the full persona id list.
 */
public enum PdlcRole {
    PO, RESEARCHER, ARCHITECT, DESIGNER, PLANNER,
    IMPLEMENTER, REVIEWER, TESTER, SECURITY_ANALYST,
    TECHNICAL_WRITER, DEVOPS, RETRO_ANALYST
}
