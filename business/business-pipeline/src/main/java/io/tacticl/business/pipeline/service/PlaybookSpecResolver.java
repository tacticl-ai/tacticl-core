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
     * Returns a JSON string describing the playbook configuration.
     * Falls back to FULL_PDLC if the playbook name is unknown.
     */
    public String resolve(String playbookName) {
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
