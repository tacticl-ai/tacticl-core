package io.tacticl.business.pipeline.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

@Service
public class PlaybookSpecResolver {

    private static final Logger log = LoggerFactory.getLogger(PlaybookSpecResolver.class);
    private static final JsonMapper JSON = new JsonMapper();

    private static final Map<String, List<String>> PLAYBOOK_ROLES = Map.of(
        "FULL_PDLC",       List.of("PM", "RESEARCHER", "ARCHITECT", "DESIGNER", "PLANNER",
                                    "IMPLEMENTER", "REVIEWER", "TESTER", "SECURITY_ANALYST",
                                    "TECHNICAL_WRITER", "DEVOPS", "RETRO_ANALYST"),
        "BUG_FIX",         List.of("RESEARCHER", "IMPLEMENTER", "REVIEWER", "TESTER", "RETRO_ANALYST"),
        "SMALL_FEATURE",   List.of("PM", "PLANNER", "IMPLEMENTER", "REVIEWER", "TESTER", "RETRO_ANALYST"),
        "REFACTOR",        List.of("RESEARCHER", "PLANNER", "IMPLEMENTER", "REVIEWER", "RETRO_ANALYST"),
        "INFRA_CHANGE",    List.of("ARCHITECT", "PLANNER", "DEVOPS", "REVIEWER", "RETRO_ANALYST"),
        "DOCS_ONLY",       List.of("RESEARCHER", "TECHNICAL_WRITER", "REVIEWER", "RETRO_ANALYST"),
        "UI_CHANGE",       List.of("DESIGNER", "IMPLEMENTER", "REVIEWER", "TESTER", "RETRO_ANALYST"),
        "SECURITY_PATCH",  List.of("RESEARCHER", "SECURITY_ANALYST", "IMPLEMENTER", "REVIEWER", "RETRO_ANALYST")
    );

    /**
     * Arbiter Temporal pipelines ({@code pdlc-fix} | {@code pdlc-feature}) consume a
     * {@code PdlcPlaybookConfig} ({@code mergeGateRole}/{@code reworkEntryRole}/{@code reworkMax}),
     * NOT the legacy {@code {name, roles}} shape the in-JVM shell path uses. The arbiter's merge
     * gate — the single human PR-approval checkpoint — engages ONLY when {@code mergeGateRole} is
     * set (cidadel-ai-arbiter resolve-bundle.ts). These configs are keyed to the lean
     * {@code pdlc-fix} registry DAG (investigator → implementer → test): the gate sits at
     * {@code test} and a GRANT_REWORK re-opens {@code implementer}.
     */
    private static final Map<String, Map<String, Object>> ARBITER_PLAYBOOK_CONFIG = Map.of(
        "pdlc-fix", Map.of(
            "mergeGateRole", "test",           // park for human PR approval once tests pass
            "reworkEntryRole", "implementer",  // a GRANT_REWORK re-opens the implementer
            "reworkMax", 1),                    // lean: one rework round, then escalate
        // User-initiated BUG_FIX — the default playbook for onboarded products and the
        // Discord/Telegram EntryPoint default. Gate at TESTER (a node in the BUG_FIX DAG:
        // researcher → implementer → reviewer → tester → retro) so research/implement/review/
        // test all run, then the host pushes the branch + opens the PR and parks for the single
        // human merge-approval. Without this, BUG_FIX fell to the legacy {name,roles} shape and
        // the arbiter merge gate never engaged → no PR.
        "BUG_FIX", Map.of(
            "mergeGateRole", "tester",
            "reworkEntryRole", "implementer",
            "reworkMax", 1)
    );

    /**
     * Returns a JSON string describing the playbook configuration. For an arbiter Temporal
     * pipeline this is a {@code PdlcPlaybookConfig}; otherwise the legacy {@code {name, roles}}
     * spec (falling back to FULL_PDLC for an unknown legacy name).
     */
    public String resolve(String playbookName) {
        Map<String, Object> arbiterConfig = ARBITER_PLAYBOOK_CONFIG.get(playbookName);
        if (arbiterConfig != null) {
            try {
                return JSON.writeValueAsString(arbiterConfig);
            } catch (JacksonException e) {
                throw new RuntimeException("Failed to serialize arbiter playbook config", e);
            }
        }

        List<String> roles = PLAYBOOK_ROLES.get(playbookName);
        if (roles == null) {
            log.warn("Unknown playbook '{}', falling back to FULL_PDLC", playbookName);
            roles = PLAYBOOK_ROLES.get("FULL_PDLC");
        }
        Map<String, Object> spec = Map.of(
            "name", playbookName,
            "roles", roles
        );
        try {
            return JSON.writeValueAsString(spec);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize playbook spec", e);
        }
    }
}
