package io.strategiz.social.service.checkpoint.controller;

import io.strategiz.framework.authorization.annotation.AuthUser;
import io.strategiz.framework.authorization.annotation.RequireAuth;
import io.strategiz.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.business.agent.service.SparkService;
import io.strategiz.social.data.entity.Checkpoint;
import io.strategiz.social.data.entity.CheckpointDecision;
import io.strategiz.social.service.checkpoint.dto.CheckpointResponse;
import io.strategiz.social.service.checkpoint.dto.DecideCheckpointRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for checkpoint approval workflow. */
@RestController
@RequestMapping("/api/checkpoints")
@Tag(name = "Checkpoints", description = "Review and approve/reject execution checkpoints")
public class CheckpointController {

	private static final Logger log = LoggerFactory.getLogger(CheckpointController.class);

	private final SparkService sparkService;

	public CheckpointController(SparkService sparkService) {
		this.sparkService = sparkService;
	}

	@GetMapping
	@RequireAuth
	@Operation(summary = "List checkpoints", description = "List all checkpoints for the user's sparks")
	public ResponseEntity<List<CheckpointResponse>> listCheckpoints(@AuthUser AuthenticatedUser user) {
		List<Checkpoint> checkpoints = sparkService.getCheckpointsForUser(user.getUserId());
		List<CheckpointResponse> response = checkpoints.stream().map(this::toResponse).toList();
		return ResponseEntity.ok(response);
	}

	@GetMapping("/{id}")
	@RequireAuth
	@Operation(summary = "Get checkpoint detail")
	public ResponseEntity<CheckpointResponse> getCheckpoint(@PathVariable String id,
			@AuthUser AuthenticatedUser user) {
		return sparkService.getCheckpoint(id)
			.map(this::toResponse)
			.map(ResponseEntity::ok)
			.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping("/{id}/decide")
	@RequireAuth
	@Operation(summary = "Decide on a checkpoint", description = "Approve, reject, or modify a checkpoint")
	public ResponseEntity<CheckpointResponse> decideCheckpoint(@PathVariable String id,
			@Valid @RequestBody DecideCheckpointRequest request, @AuthUser AuthenticatedUser user) {
		log.info("Checkpoint decision for {} by user {}: {}", id, user.getUserId(), request.getDecision());

		CheckpointDecision decision;
		try {
			decision = CheckpointDecision.valueOf(request.getDecision().toUpperCase());
		}
		catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().build();
		}

		Optional<Checkpoint> decided = sparkService.decideCheckpoint(id, user.getUserId(), decision,
				request.getFeedback());

		return decided.map(this::toResponse).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	private CheckpointResponse toResponse(Checkpoint checkpoint) {
		CheckpointResponse r = new CheckpointResponse();
		r.setId(checkpoint.getId());
		r.setSparkId(checkpoint.getSparkId());
		r.setTacticId(checkpoint.getTacticId());
		r.setTitle(checkpoint.getTitle());
		r.setDescription(checkpoint.getDescription());
		r.setFindings(checkpoint.getFindings());
		r.setOptions(checkpoint.getOptions());
		r.setUserDecision(checkpoint.getUserDecision() != null ? checkpoint.getUserDecision().name() : null);
		r.setUserFeedback(checkpoint.getUserFeedback());
		r.setDecidedAt(checkpoint.getDecidedAt());
		r.setCreatedAt(checkpoint.getCreatedAt());
		return r;
	}

}
