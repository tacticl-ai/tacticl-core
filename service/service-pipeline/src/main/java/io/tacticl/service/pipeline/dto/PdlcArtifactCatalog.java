package io.tacticl.service.pipeline.dto;

import io.tacticl.data.pipeline.entity.PdlcRole;
import java.util.List;

/**
 * Stable role → artifact-name mapping for the PDLC Artifacts viewer.
 *
 * <p>Each PDLC role commits exactly one canonical markdown artifact under
 * {@code .tacticl/pdlc/{runId}/<name>.md}. This catalog is the single source of truth for:
 * <ul>
 *   <li>the artifact file <b>stem</b> ({@link CatalogEntry#name()}) used to read content via
 *       {@code GET /v1/sparks/{id}/pipeline/artifacts/{name}/content}, and</li>
 *   <li>the human-readable rail <b>label</b> ({@link CatalogEntry#title()}), and</li>
 *   <li>the owning {@link PdlcRole}, used to group the rail and derive status from the run's
 *       per-role results.</li>
 * </ul>
 *
 * <p>The on-disk {@code manifest.json} (when the agents commit one) is authoritative for any
 * entry it lists — its {@code path}/{@code title}/{@code agent} override the catalog defaults.
 * This catalog supplies the full expected skeleton so the rail can also render <em>pending</em>
 * roles that have not committed an artifact yet.
 *
 * <p>The 12 mappings match the {@link PdlcRole} enum order. Names were chosen to match the
 * arbiter's emitted artifact stems where known; adjust here (the only place) if the arbiter
 * settles on different stems.
 */
public final class PdlcArtifactCatalog {

    private PdlcArtifactCatalog() {}

    /**
     * One canonical role → artifact mapping.
     *
     * @param role  the owning PDLC role
     * @param name  the artifact file stem (no {@code .md}), e.g. {@code "product-brief"}
     * @param title human-readable rail label, e.g. {@code "Product Brief"}
     */
    public record CatalogEntry(PdlcRole role, String name, String title) {}

    /** Ordered canonical catalog — one entry per {@link PdlcRole}, in pipeline order. */
    public static final List<CatalogEntry> ENTRIES = List.of(
        new CatalogEntry(PdlcRole.PO,               "product-brief",   "Product Brief"),
        new CatalogEntry(PdlcRole.RESEARCHER,       "research",        "Research"),
        new CatalogEntry(PdlcRole.ARCHITECT,        "architecture",    "Architecture"),
        new CatalogEntry(PdlcRole.DESIGNER,         "design",          "Design"),
        new CatalogEntry(PdlcRole.PLANNER,          "plan",            "Plan"),
        new CatalogEntry(PdlcRole.IMPLEMENTER,      "change-summary",  "Change Summary"),
        new CatalogEntry(PdlcRole.TESTER,           "test-report",     "Test Report"),
        new CatalogEntry(PdlcRole.SECURITY_ANALYST, "security-report", "Security Report"),
        new CatalogEntry(PdlcRole.REVIEWER,         "review",          "Review"),
        new CatalogEntry(PdlcRole.TECHNICAL_WRITER, "docs",            "Docs"),
        new CatalogEntry(PdlcRole.DEVOPS,           "devops",          "DevOps"),
        new CatalogEntry(PdlcRole.RETRO_ANALYST,    "retro",           "Retro")
    );

    /** Lookup the catalog entry for a role, or {@code null} when the role is unmapped. */
    public static CatalogEntry forRole(PdlcRole role) {
        if (role == null) return null;
        for (CatalogEntry e : ENTRIES) {
            if (e.role() == role) return e;
        }
        return null;
    }

    /** Lookup the catalog entry for an artifact stem, or {@code null} when unknown. */
    public static CatalogEntry forName(String name) {
        if (name == null) return null;
        for (CatalogEntry e : ENTRIES) {
            if (e.name().equals(name)) return e;
        }
        return null;
    }
}
