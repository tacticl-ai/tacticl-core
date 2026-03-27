package io.strategiz.social.service.agent.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.business.agent.pipeline.CheckpointService;
import io.strategiz.social.business.agent.pipeline.PipelineArtifactService;
import io.strategiz.social.business.agent.pipeline.PlaybookRegistry;
import io.strategiz.social.data.entity.Checkpoint;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineArtifact;
import io.strategiz.social.data.entity.PipelineEvent;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.Spark;
import io.strategiz.social.data.repository.PipelineEventRepository;
import io.strategiz.social.data.repository.PipelineRunRepository;
import io.strategiz.social.data.repository.SparkRepository;
import io.strategiz.social.service.agent.dto.CheckpointResolutionRequest;
import io.strategiz.social.service.agent.dto.PipelineEventResponse;
import io.strategiz.social.service.agent.dto.PipelineRunResponse;
import io.strategiz.social.service.agent.dto.PlaybookResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller exposing pipeline status, events, artifacts, checkpoint resolution, and playbook listing. */
@RestController
@RequestMapping("/v1")
@Tag(name = "Pipeline", description = "PDLC pipeline status, events, artifacts, and playbook management")
public class PipelineController {

	private static final Logger log = LoggerFactory.getLogger(PipelineController.class);

	private final PipelineRunRepository pipelineRunRepository;

	private final PipelineEventRepository pipelineEventRepository;

	private final PipelineArtifactService pipelineArtifactService;

	private final PlaybookRegistry playbookRegistry;

	private final CheckpointService checkpointService;

	private final SparkRepository sparkRepository;

	public PipelineController(PipelineRunRepository pipelineRunRepository,
			PipelineEventRepository pipelineEventRepository,
			PipelineArtifactService pipelineArtifactService,
			PlaybookRegistry playbookRegistry,
			CheckpointService checkpointService,
			SparkRepository sparkRepository) {
		this.pipelineRunRepository = pipelineRunRepository;
		this.pipelineEventRepository = pipelineEventRepository;
		this.pipelineArtifactService = pipelineArtifactService;
		this.playbookRegistry = playbookRegistry;
		this.checkpointService = checkpointService;
		this.sparkRepository = sparkRepository;
	}

	@GetMapping("/sparks/{sparkId}/pipeline")
	@RequireAuth
	@Operation(summary = "Get pipeline run for a spark",
			description = "Returns the current pipeline run state for the given spark, including status, roles, and cost.")
	public ResponseEntity<PipelineRunResponse> getPipeline(@PathVariable String sparkId,
			@AuthUser AuthenticatedUser user) {
		log.debug("Get pipeline for spark={} user={}", sparkId, user.getUserId());

		Optional<PipelineRun> runOpt = pipelineRunRepository.findBySparkId(sparkId);
		if (runOpt.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		PipelineRun run = runOpt.get();
		if (!run.getUserId().equals(user.getUserId())) {
			return ResponseEntity.status(403).build();
		}

		return ResponseEntity.ok(PipelineRunResponse.from(run));
	}

	@GetMapping("/sparks/{sparkId}/pipeline/events")
	@RequireAuth
	@Operation(summary = "Get pipeline events for a spark",
			description = "Returns the ordered list of pipeline events for the given spark's pipeline run. Supports optional pagination.")
	public ResponseEntity<List<PipelineEventResponse>> getPipelineEvents(@PathVariable String sparkId,
			@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) Integer offset,
			@AuthUser AuthenticatedUser user) {
		log.debug("Get pipeline events for spark={} user={} limit={} offset={}", sparkId, user.getUserId(), limit, offset);

		Optional<PipelineRun> runOpt = pipelineRunRepository.findBySparkId(sparkId);
		if (runOpt.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		PipelineRun run = runOpt.get();
		if (!run.getUserId().equals(user.getUserId())) {
			return ResponseEntity.status(403).build();
		}

		List<PipelineEvent> events = pipelineEventRepository.findByPipelineRunId(run.getId());

		// Apply pagination if requested
		int start = offset != null && offset > 0 ? offset : 0;
		int end = limit != null && limit > 0 ? Math.min(start + limit, events.size()) : events.size();
		if (start > events.size()) {
			start = events.size();
		}
		List<PipelineEvent> page = events.subList(start, end);

		List<PipelineEventResponse> response = page.stream()
			.map(PipelineEventResponse::from)
			.toList();

		return ResponseEntity.ok(response);
	}

	@GetMapping("/sparks/{sparkId}/pipeline/artifacts/{role}")
	@RequireAuth
	@Operation(summary = "Get pipeline artifact for a role",
			description = "Returns the artifact produced by the specified PDLC role for this spark's pipeline run.")
	public ResponseEntity<Map<String, Object>> getArtifact(@PathVariable String sparkId,
			@PathVariable String role,
			@AuthUser AuthenticatedUser user) {
		log.debug("Get pipeline artifact for spark={} role={} user={}", sparkId, role, user.getUserId());

		Optional<PipelineRun> runOpt = pipelineRunRepository.findBySparkId(sparkId);
		if (runOpt.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		PipelineRun run = runOpt.get();
		if (!run.getUserId().equals(user.getUserId())) {
			return ResponseEntity.status(403).build();
		}

		PdlcRole pdlcRole;
		try {
			pdlcRole = PdlcRole.valueOf(role.toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			return ResponseEntity.badRequest().build();
		}

		Optional<PipelineArtifact> artifactOpt = pipelineArtifactService.getArtifactForRole(run.getId(), pdlcRole);
		if (artifactOpt.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		return ResponseEntity.ok(artifactOpt.get().getContent());
	}

	@PostMapping("/sparks/{sparkId}/pipeline/checkpoint/{checkpointId}")
	@RequireAuth
	@Operation(summary = "Resolve a pipeline checkpoint",
			description = "Approve, reject, or modify a pipeline checkpoint gate. Requires APPROVED, REJECTED, or MODIFIED decision.")
	public ResponseEntity<Void> resolveCheckpoint(@PathVariable String sparkId,
			@PathVariable String checkpointId,
			@Valid @RequestBody CheckpointResolutionRequest request,
			@AuthUser AuthenticatedUser user) {
		log.info("Checkpoint resolution for spark={} checkpointId={} decision={} user={}", sparkId, checkpointId,
				request.getDecision(), user.getUserId());

		// 1. Load checkpoint and verify it belongs to this spark
		Optional<Checkpoint> checkpointOpt = checkpointService.getCheckpoint(checkpointId);
		if (checkpointOpt.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		Checkpoint checkpoint = checkpointOpt.get();
		if (!sparkId.equals(checkpoint.getSparkId())) {
			return ResponseEntity.status(403).build();
		}

		// 2. Verify the spark belongs to this user
		if (checkpoint.getPipelineRunId() != null) {
			Optional<PipelineRun> runOpt = pipelineRunRepository.findById(checkpoint.getPipelineRunId());
			if (runOpt.isPresent() && !runOpt.get().getUserId().equals(user.getUserId())) {
				return ResponseEntity.status(403).build();
			}
		}
		else {
			// No pipeline run linked — verify ownership directly via the Spark entity
			Optional<Spark> sparkOpt = sparkRepository.findById(sparkId);
			if (sparkOpt.isEmpty() || !sparkOpt.get().getUserId().equals(user.getUserId())) {
				return ResponseEntity.status(403).build();
			}
		}

		// 3. Apply the decision — CheckpointService validates state and resumes the pipeline
		try {
			checkpointService.resolveCheckpoint(checkpointId, user.getUserId(),
					request.getDecision(), request.getFeedback());
		}
		catch (IllegalStateException ex) {
			log.warn("Checkpoint already resolved: checkpointId={} user={}", checkpointId, user.getUserId());
			return ResponseEntity.status(409).build();
		}
		catch (IllegalArgumentException ex) {
			log.warn("Invalid checkpoint resolution: checkpointId={} error={}", checkpointId, ex.getMessage());
			return ResponseEntity.badRequest().build();
		}

		return ResponseEntity.accepted().build();
	}

	@GetMapping("/playbooks")
	@RequireAuth
	@Operation(summary = "List all available playbooks",
			description = "Returns all registered playbooks, including system playbooks and their stage configurations.")
	public ResponseEntity<List<PlaybookResponse>> getPlaybooks(@AuthUser AuthenticatedUser user) {
		log.debug("List playbooks requested by user={}", user.getUserId());

		List<PlaybookResponse> playbooks = playbookRegistry.getAllPlaybooks().stream()
			.map(PlaybookResponse::from)
			.toList();

		return ResponseEntity.ok(playbooks);
	}

}
