package io.tacticl.service.pipeline.controller;

import io.cidadel.service.base.controller.BaseController;
import io.tacticl.service.pipeline.dto.PlaybookDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/playbooks")
public class PlaybooksController extends BaseController {

    private static final List<PlaybookDto> PLAYBOOKS = List.of(
        new PlaybookDto("FULL_PDLC", "Full PDLC", "Complete 12-role pipeline for large features", "FULL_PDLC",
            List.of("PM", "RESEARCHER", "ARCHITECT", "DESIGNER", "PLANNER", "IMPLEMENTER",
                    "REVIEWER", "TESTER", "SECURITY_ANALYST", "TECHNICAL_WRITER", "DEVOPS", "RETRO_ANALYST"),
            true),
        new PlaybookDto("BUG_FIX", "Bug Fix", "Focused pipeline for bug fixes", "PLAYBOOK",
            List.of("RESEARCHER", "IMPLEMENTER", "REVIEWER", "TESTER", "RETRO_ANALYST"), true),
        new PlaybookDto("SMALL_FEATURE", "Small Feature", "Lightweight pipeline for small features", "PLAYBOOK",
            List.of("PM", "PLANNER", "IMPLEMENTER", "REVIEWER", "TESTER", "RETRO_ANALYST"), true),
        new PlaybookDto("REFACTOR", "Refactor", "Code quality and refactoring pipeline", "PLAYBOOK",
            List.of("RESEARCHER", "PLANNER", "IMPLEMENTER", "REVIEWER", "RETRO_ANALYST"), true),
        new PlaybookDto("INFRA_CHANGE", "Infrastructure Change", "Infrastructure and DevOps changes", "PLAYBOOK",
            List.of("ARCHITECT", "PLANNER", "DEVOPS", "REVIEWER", "RETRO_ANALYST"), true),
        new PlaybookDto("DOCS_ONLY", "Documentation", "Documentation updates", "PLAYBOOK",
            List.of("RESEARCHER", "TECHNICAL_WRITER", "REVIEWER", "RETRO_ANALYST"), true),
        new PlaybookDto("UI_CHANGE", "UI Change", "Frontend and UI changes", "PLAYBOOK",
            List.of("DESIGNER", "IMPLEMENTER", "REVIEWER", "TESTER", "RETRO_ANALYST"), true),
        new PlaybookDto("SECURITY_PATCH", "Security Patch", "Security-focused pipeline", "PLAYBOOK",
            List.of("RESEARCHER", "SECURITY_ANALYST", "IMPLEMENTER", "REVIEWER", "RETRO_ANALYST"), true)
    );

    @Override
    protected String getModuleName() { return "playbooks"; }

    @GetMapping
    public ResponseEntity<List<PlaybookDto>> getPlaybooks() {
        return ResponseEntity.ok(PLAYBOOKS);
    }
}
