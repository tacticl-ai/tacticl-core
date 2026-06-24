package io.tacticl.service.pipeline.dto;

import io.tacticl.business.pipeline.service.ArtifactRetrievalService;
import io.tacticl.data.pipeline.entity.PdlcRole;
import io.tacticl.data.pipeline.entity.PhaseState;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.entity.RoleState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One entry in the PDLC Artifacts manifest the viewer renders as a grouped rail.
 *
 * <p>The manifest is the <b>union</b> of:
 * <ol>
 *   <li>the canonical {@link PdlcArtifactCatalog} skeleton (every expected role artifact, so the
 *       rail can show <em>pending</em> roles that have not committed yet), enriched/overridden by</li>
 *   <li>the committed {@code .tacticl/pdlc/{runId}/manifest.json} entries (authoritative
 *       {@code path}/{@code title}/{@code agent}/{@code summary} for artifacts that exist), and</li>
 *   <li>per-role {@code status} derived from the run's role results.</li>
 * </ol>
 *
 * @param name     artifact file stem without {@code .md}, e.g. {@code "product-brief"} —
 *                 the key for {@code GET …/artifacts/{name}/content}
 * @param role     owning {@link PdlcRole} (e.g. {@code PO}); null only for manifest entries
 *                 whose agent is not a known PDLC role
 * @param title    human-readable rail label, e.g. {@code "Product Brief"}
 * @param path     repo path the artifact lives at, e.g.
 *                 {@code .tacticl/pdlc/{runId}/product-brief.md} (null until committed)
 * @param status   rail status: {@code "done"} | {@code "active"} | {@code "pending"}
 * @param version  artifact version, e.g. {@code "v1"}/{@code "v2"} (rework-derived; {@code "v1"} default)
 * @param present  whether real markdown content exists for this artifact (it was in the
 *                 committed manifest)
 * @param type     manifest {@code type} hint (e.g. {@code "markdown"}); empty when not committed
 * @param agent    manifest {@code agent} string as committed; empty when not committed
 * @param summary  manifest {@code summary} blurb; empty when not committed
 */
public record ArtifactManifestEntryDto(
    String name,
    PdlcRole role,
    String title,
    String path,
    String status,
    String version,
    boolean present,
    String type,
    String agent,
    String summary
) {

    private static final String STATUS_DONE = "done";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_PENDING = "pending";

    /**
     * Build the enriched union manifest for a run.
     *
     * <p>Starts from the canonical {@link PdlcArtifactCatalog} (one entry per role, ordered),
     * enriches each with the committed manifest entry (if present) and the role's derived status,
     * then appends any committed entries that did not map onto a catalog role (so nothing the
     * agents emitted is dropped). Fully null-safe.
     *
     * @param run               the pipeline run (may be null → returns canonical skeleton, all pending)
     * @param committedEntries  parsed {@code manifest.json} entries (may be null/empty)
     * @return ordered, never-null manifest
     */
    public static List<ArtifactManifestEntryDto> build(
            PipelineRun run,
            List<ArtifactRetrievalService.ManifestEntry> committedEntries) {

        Map<String, RoleState> roleStates = collectRoleStates(run);

        // Index committed entries by basename for O(1) enrichment + leftover tracking.
        Map<String, ArtifactRetrievalService.ManifestEntry> committedByName = new LinkedHashMap<>();
        if (committedEntries != null) {
            for (ArtifactRetrievalService.ManifestEntry e : committedEntries) {
                if (e == null) continue;
                String key = basenameOf(e);
                if (key != null && !key.isBlank()) {
                    committedByName.putIfAbsent(key, e);
                }
            }
        }

        List<ArtifactManifestEntryDto> out = new ArrayList<>();

        // 1) Canonical skeleton, enriched.
        for (PdlcArtifactCatalog.CatalogEntry cat : PdlcArtifactCatalog.ENTRIES) {
            ArtifactRetrievalService.ManifestEntry committed = committedByName.remove(cat.name());
            RoleState roleState = roleStates.get(cat.role().name());

            boolean present = committed != null;
            String path = committed != null && notBlank(committed.path())
                ? committed.path() : null;
            String title = committed != null && notBlank(committed.title())
                ? committed.title() : cat.title();
            String type = committed != null ? nullToEmpty(committed.type()) : "";
            String agent = committed != null ? nullToEmpty(committed.agent()) : "";
            String summary = committed != null ? nullToEmpty(committed.summary()) : "";

            out.add(new ArtifactManifestEntryDto(
                cat.name(),
                cat.role(),
                title,
                path,
                deriveStatus(roleState, present),
                deriveVersion(roleState),
                present,
                type,
                agent,
                summary
            ));
        }

        // 2) Leftover committed entries not in the canonical catalog (never drop emitted work).
        for (ArtifactRetrievalService.ManifestEntry e : committedByName.values()) {
            String name = basenameOf(e);
            PdlcRole role = parseRole(e.agent());
            RoleState roleState = role != null ? roleStates.get(role.name()) : null;
            out.add(new ArtifactManifestEntryDto(
                name,
                role,
                notBlank(e.title()) ? e.title() : name,
                notBlank(e.path()) ? e.path() : null,
                deriveStatus(roleState, true),
                deriveVersion(roleState),
                true,
                nullToEmpty(e.type()),
                nullToEmpty(e.agent()),
                nullToEmpty(e.summary())
            ));
        }

        return out;
    }

    /** Flatten every role's {@link RoleState} across all phases, keyed by role name. */
    private static Map<String, RoleState> collectRoleStates(PipelineRun run) {
        Map<String, RoleState> roleStates = new LinkedHashMap<>();
        if (run == null || run.getPhases() == null) {
            return roleStates;
        }
        for (PhaseState phase : run.getPhases().values()) {
            if (phase == null || phase.getRoles() == null) continue;
            for (Map.Entry<String, RoleState> entry : phase.getRoles().entrySet()) {
                // Last write wins; phases are role-disjoint in practice.
                roleStates.put(entry.getKey(), entry.getValue());
            }
        }
        return roleStates;
    }

    /**
     * Derive the rail status. Precedence: an explicit COMPLETED/RUNNING role state wins; otherwise
     * a present-on-disk artifact counts as done; otherwise pending. A failed role with no artifact
     * is surfaced as pending (the rail uses run-level status for failures).
     */
    private static String deriveStatus(RoleState roleState, boolean present) {
        if (roleState != null && roleState.getStatus() != null) {
            switch (roleState.getStatus()) {
                case "COMPLETED" -> { return STATUS_DONE; }
                case "RUNNING" -> { return STATUS_ACTIVE; }
                default -> { /* PENDING / FAILED / SKIPPED fall through */ }
            }
        }
        return present ? STATUS_DONE : STATUS_PENDING;
    }

    /** {@code "v" + (reworkCount + 1)} — first pass is {@code v1}, each rework bumps it. */
    private static String deriveVersion(RoleState roleState) {
        int rework = roleState != null ? Math.max(0, roleState.getReworkCount()) : 0;
        return "v" + (rework + 1);
    }

    /** Basename without {@code .md} — prefers the manifest {@code artifact_id}, falls back to {@code path}. */
    private static String basenameOf(ArtifactRetrievalService.ManifestEntry e) {
        if (notBlank(e.artifactId())) {
            return stripMd(lastSegment(e.artifactId()));
        }
        if (notBlank(e.path())) {
            return stripMd(lastSegment(e.path()));
        }
        return null;
    }

    private static String lastSegment(String s) {
        String norm = s.replace('\\', '/');
        int slash = norm.lastIndexOf('/');
        return slash >= 0 ? norm.substring(slash + 1) : norm;
    }

    private static String stripMd(String s) {
        return s.endsWith(".md") ? s.substring(0, s.length() - 3) : s;
    }

    /** Tolerantly parse a manifest {@code agent} string into a {@link PdlcRole}, else null. */
    private static PdlcRole parseRole(String agent) {
        if (agent == null || agent.isBlank()) return null;
        String norm = agent.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        try {
            return PdlcRole.valueOf(norm);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
