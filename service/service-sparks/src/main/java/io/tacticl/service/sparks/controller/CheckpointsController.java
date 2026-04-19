package io.tacticl.service.sparks.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.sparks.service.CheckpointService;
import io.tacticl.data.sparks.entity.Checkpoint;
import io.tacticl.data.sparks.entity.CheckpointStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/checkpoints")
public class CheckpointsController extends BaseController {

    private final CheckpointService checkpointService;

    public CheckpointsController(CheckpointService checkpointService) {
        this.checkpointService = checkpointService;
    }

    @Override
    protected String getModuleName() {
        return "checkpoints";
    }

    @GetMapping
    @RequireAuth
    public ResponseEntity<List<Map<String, Object>>> listCheckpoints(
            @AuthUser AuthenticatedUser user) {
        List<Map<String, Object>> result = checkpointService.listByUser(user.getUserId())
                .stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @RequireAuth
    public ResponseEntity<Map<String, Object>> getCheckpoint(
            @PathVariable String id,
            @AuthUser AuthenticatedUser user) {
        return checkpointService.findByUser(id, user.getUserId())
                .map(cp -> ResponseEntity.ok(toDto(cp)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/decide")
    @RequireAuth
    public ResponseEntity<Map<String, Object>> decideCheckpoint(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @AuthUser AuthenticatedUser user) {
        Checkpoint cp = checkpointService.findByUser(id, user.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkpoint not found"));
        String decision = body.get("decision");
        CheckpointStatus status = "APPROVED".equals(decision)
                ? CheckpointStatus.APPROVED
                : CheckpointStatus.DENIED;
        String feedback = body.get("feedback");
        Checkpoint resolved = checkpointService.resolve(
                id, cp.getSparkId(), user.getUserId(), status, feedback);
        return ResponseEntity.ok(toDto(resolved));
    }

    private Map<String, Object> toDto(Checkpoint cp) {
        String userDecision = cp.getStatus() == CheckpointStatus.PENDING
                ? null
                : cp.getStatus() == CheckpointStatus.APPROVED ? "APPROVED" : "REJECTED";

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", cp.getId());
        dto.put("sparkId", cp.getSparkId());
        dto.put("tacticId", null);
        dto.put("title", cp.getType() != null ? cp.getType().name() : null);
        dto.put("description", cp.getPrompt() != null ? cp.getPrompt() : "");
        dto.put("findings", List.of());
        dto.put("options", List.of());
        dto.put("userDecision", userDecision);
        dto.put("userFeedback", cp.getResolutionInstructions());
        dto.put("decidedAt", cp.getResolvedAt() != null ? cp.getResolvedAt().toString() : null);
        dto.put("createdAt", cp.getCreatedAt() != null ? cp.getCreatedAt().toString() : "");
        return dto;
    }
}
