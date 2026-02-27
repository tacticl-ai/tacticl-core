package io.strategiz.social.service.spark.controller;

import io.strategiz.framework.authorization.annotation.AuthUser;
import io.strategiz.framework.authorization.annotation.RequireAuth;
import io.strategiz.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.business.agent.service.SparkDispatchService;
import io.strategiz.social.business.agent.service.SparkService;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.ExecutionLog;
import io.strategiz.social.data.entity.Spark;
import io.strategiz.social.data.entity.SparkState;
import io.strategiz.social.data.entity.Tactic;
import io.strategiz.social.service.spark.dto.ExecutionLogResponse;
import io.strategiz.social.service.spark.dto.SparkActivityResponse;
import io.strategiz.social.service.spark.dto.SparkResponse;
import io.strategiz.social.service.spark.dto.TacticResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for spark read, execution, and lifecycle operations. */
@RestController
@RequestMapping("/api/sparks")
@Tag(name = "Sparks", description = "Manage and execute sparks")
public class SparkController {

	private static final Logger log = LoggerFactory.getLogger(SparkController.class);

	private final SparkService sparkService;

	private final SparkDispatchService sparkDispatchService;

	public SparkController(SparkService sparkService, SparkDispatchService sparkDispatchService) {
		this.sparkService = sparkService;
		this.sparkDispatchService = sparkDispatchService;
	}

	@GetMapping("/activity")
	@RequireAuth
	@Operation(summary = "Get activity dashboard data",
			description = "Returns active and recent sparks for the activity dashboard")
	public ResponseEntity<SparkActivityResponse> getActivity(@AuthUser AuthenticatedUser user) {
		List<Spark> active = sparkService.getActiveSparks(user.getUserId());
		List<Spark> recent = sparkService.getRecentSparks(user.getUserId(), 10);

		SparkActivityResponse response = new SparkActivityResponse();
		response.setActiveSparks(active.stream().map(this::toResponse).toList());
		response.setRecentSparks(recent.stream()
			.filter(s -> s.getStatus() == SparkState.COMPLETED || s.getStatus() == SparkState.FAILED
					|| s.getStatus() == SparkState.CANCELLED)
			.map(this::toResponse)
			.limit(10)
			.toList());
		return ResponseEntity.ok(response);
	}

	@GetMapping
	@RequireAuth
	@Operation(summary = "List sparks", description = "List recent sparks for the authenticated user")
	public ResponseEntity<List<SparkResponse>> listSparks(@AuthUser AuthenticatedUser user,
			@RequestParam(defaultValue = "50") int limit) {
		List<Spark> sparks = sparkService.listSparks(user.getUserId(), limit);
		List<SparkResponse> response = sparks.stream().map(this::toResponse).toList();
		return ResponseEntity.ok(response);
	}

	@GetMapping("/{id}")
	@RequireAuth
	@Operation(summary = "Get spark detail")
	public ResponseEntity<SparkResponse> getSpark(@PathVariable String id, @AuthUser AuthenticatedUser user) {
		return sparkService.getSpark(id, user.getUserId())
			.map(this::toResponse)
			.map(ResponseEntity::ok)
			.orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{id}")
	@RequireAuth
	@Operation(summary = "Delete a spark", description = "Soft-delete a spark")
	public ResponseEntity<Void> deleteSpark(@PathVariable String id, @AuthUser AuthenticatedUser user) {
		boolean deleted = sparkService.deleteSpark(id, user.getUserId());
		return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	@PostMapping("/{id}/run")
	@RequireAuth
	@Operation(summary = "Run a spark", description = "Route the spark to a device and begin execution")
	public ResponseEntity<SparkResponse> runSpark(@PathVariable String id, @AuthUser AuthenticatedUser user) {
		Optional<Spark> sparkOpt = sparkService.getSpark(id, user.getUserId());
		if (sparkOpt.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		Optional<DeviceRegistration> device = sparkService.routeSpark(id, user.getUserId());
		if (device.isPresent()) {
			sparkService.getSparkInternal(id).ifPresent(sparkDispatchService::dispatchSpark);
		}

		return sparkService.getSpark(id, user.getUserId())
			.map(this::toResponse)
			.map(ResponseEntity::ok)
			.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping("/{id}/cancel")
	@RequireAuth
	@Operation(summary = "Cancel a spark", description = "Cancel execution and notify the device")
	public ResponseEntity<SparkResponse> cancelSpark(@PathVariable String id, @AuthUser AuthenticatedUser user) {
		Optional<Spark> sparkOpt = sparkService.getSpark(id, user.getUserId());
		if (sparkOpt.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		// Send cancel to device before updating state
		sparkDispatchService.cancelSparkOnDevice(sparkOpt.get());

		boolean cancelled = sparkService.cancelSpark(id, user.getUserId());
		if (!cancelled) {
			return ResponseEntity.badRequest().build();
		}

		return sparkService.getSpark(id, user.getUserId())
			.map(this::toResponse)
			.map(ResponseEntity::ok)
			.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/{id}/tactics")
	@RequireAuth
	@Operation(summary = "Get tactics for a spark")
	public ResponseEntity<List<TacticResponse>> getTactics(@PathVariable String id, @AuthUser AuthenticatedUser user) {
		Optional<Spark> spark = sparkService.getSpark(id, user.getUserId());
		if (spark.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		List<Tactic> tactics = sparkService.getTactics(id);
		List<TacticResponse> response = tactics.stream().map(this::toTacticResponse).toList();
		return ResponseEntity.ok(response);
	}

	@GetMapping("/{id}/logs")
	@RequireAuth
	@Operation(summary = "Get execution logs for a spark")
	public ResponseEntity<List<ExecutionLogResponse>> getLogs(@PathVariable String id,
			@AuthUser AuthenticatedUser user) {
		Optional<Spark> spark = sparkService.getSpark(id, user.getUserId());
		if (spark.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		List<ExecutionLog> logs = sparkService.getLogs(id);
		List<ExecutionLogResponse> response = logs.stream().map(this::toLogResponse).toList();
		return ResponseEntity.ok(response);
	}

	private SparkResponse toResponse(Spark spark) {
		SparkResponse r = new SparkResponse();
		r.setId(spark.getId());
		r.setTitle(spark.getTitle());
		r.setDescription(spark.getDescription());
		r.setType(spark.getType());
		r.setStatus(spark.getStatus().name());
		r.setPriority(spark.getPriority().name());
		r.setDeviceId(spark.getDeviceId());
		r.setSchedule(spark.getSchedule());
		r.setCheckpointPolicy(spark.getCheckpointPolicy().name());
		r.setRepoAccess(spark.getRepoAccess());
		r.setResult(spark.getResult());
		r.setParentSparkId(spark.getParentSparkId());
		r.setTotalTokens(spark.getTotalTokens());
		r.setEstimatedCost(spark.getEstimatedCost());
		r.setCreatedAt(spark.getCreatedAt());
		r.setCompletedAt(spark.getCompletedAt());
		return r;
	}

	private TacticResponse toTacticResponse(Tactic tactic) {
		TacticResponse r = new TacticResponse();
		r.setId(tactic.getId());
		r.setSparkId(tactic.getSparkId());
		r.setDeviceId(tactic.getDeviceId());
		r.setDescription(tactic.getDescription());
		r.setStatus(tactic.getStatus().name());
		r.setRepos(tactic.getRepos());
		r.setResult(tactic.getResult());
		r.setTokenUsage(tactic.getTokenUsage());
		r.setCreatedAt(tactic.getCreatedAt());
		r.setCompletedAt(tactic.getCompletedAt());
		return r;
	}

	private ExecutionLogResponse toLogResponse(ExecutionLog logEntry) {
		ExecutionLogResponse r = new ExecutionLogResponse();
		r.setId(logEntry.getId());
		r.setSparkId(logEntry.getSparkId());
		r.setTacticId(logEntry.getTacticId());
		r.setToolName(logEntry.getToolName());
		r.setToolInput(logEntry.getToolInput());
		r.setToolOutput(logEntry.getToolOutput());
		r.setTokenUsage(logEntry.getTokenUsage());
		r.setDurationMs(logEntry.getDurationMs());
		r.setTimestamp(logEntry.getTimestamp());
		return r;
	}

}
