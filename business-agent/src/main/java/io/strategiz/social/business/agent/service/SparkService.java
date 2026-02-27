package io.strategiz.social.business.agent.service;

import io.strategiz.social.data.entity.Checkpoint;
import io.strategiz.social.data.entity.CheckpointDecision;
import io.strategiz.social.data.entity.CheckpointPolicy;
import io.strategiz.social.data.entity.DevicePreference;
import io.strategiz.social.data.entity.DeviceRegistration;
import io.strategiz.social.data.entity.ExecutionLog;
import io.strategiz.social.data.entity.FallbackPolicy;
import io.strategiz.social.data.entity.Spark;
import io.strategiz.social.data.entity.SparkPriority;
import io.strategiz.social.data.entity.SparkState;
import io.strategiz.social.data.entity.Tactic;
import io.strategiz.social.data.entity.TacticState;
import io.strategiz.social.data.repository.CheckpointRepository;
import io.strategiz.social.data.repository.DevicePreferenceRepository;
import io.strategiz.social.data.repository.ExecutionLogRepository;
import io.strategiz.social.data.repository.SparkRepository;
import io.strategiz.social.data.repository.TacticRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the Spark lifecycle: creation, device routing, progress tracking, checkpoint
 * handling, and completion. Broadcasts events to both devices and user browser sessions.
 */
@Service
public class SparkService {

	private static final Logger log = LoggerFactory.getLogger(SparkService.class);

	private final SparkRepository sparkRepository;

	private final TacticRepository tacticRepository;

	private final CheckpointRepository checkpointRepository;

	private final ExecutionLogRepository executionLogRepository;

	private final DevicePreferenceRepository devicePreferenceRepository;

	private final DeviceRoutingService deviceRoutingService;

	private final ActivityBroadcaster activityBroadcaster;

	private final Optional<UserBroadcaster> userBroadcaster;

	private final SparkClassifierService sparkClassifierService;

	private final SparkDispatchService sparkDispatchService;

	public SparkService(SparkRepository sparkRepository, TacticRepository tacticRepository,
			CheckpointRepository checkpointRepository, ExecutionLogRepository executionLogRepository,
			DevicePreferenceRepository devicePreferenceRepository, DeviceRoutingService deviceRoutingService,
			ActivityBroadcaster activityBroadcaster, Optional<UserBroadcaster> userBroadcaster,
			SparkClassifierService sparkClassifierService, SparkDispatchService sparkDispatchService) {
		this.sparkRepository = sparkRepository;
		this.tacticRepository = tacticRepository;
		this.checkpointRepository = checkpointRepository;
		this.executionLogRepository = executionLogRepository;
		this.devicePreferenceRepository = devicePreferenceRepository;
		this.deviceRoutingService = deviceRoutingService;
		this.activityBroadcaster = activityBroadcaster;
		this.userBroadcaster = userBroadcaster;
		this.sparkClassifierService = sparkClassifierService;
		this.sparkDispatchService = sparkDispatchService;
	}

	/** Create a new spark from user input. */
	public Spark createSpark(String userId, String title, String description, String type, SparkPriority priority,
			CheckpointPolicy checkpointPolicy, List<String> repoAccess, String schedule) {
		String sparkId = UUID.randomUUID().toString();

		// Auto-classify type if not provided
		String effectiveType = type;
		if (effectiveType == null || effectiveType.isBlank()) {
			effectiveType = sparkClassifierService.classifySparkType(title, description);
			log.info("[SPARK] Auto-classified spark={} as type={}", sparkId, effectiveType);
		}

		Spark spark = new Spark();
		spark.setId(sparkId);
		spark.setUserId(userId);
		spark.setTitle(title);
		spark.setDescription(description);
		spark.setType(effectiveType);
		spark.setPriority(priority != null ? priority : SparkPriority.NORMAL);
		spark.setCheckpointPolicy(checkpointPolicy != null ? checkpointPolicy : CheckpointPolicy.CHECKPOINT_MAJOR);
		spark.setRepoAccess(repoAccess != null ? repoAccess : List.of());
		spark.setSchedule(schedule);
		spark.setCreatedAt(Instant.now());

		// Handle scheduling
		if (schedule != null && !schedule.isBlank()) {
			spark.setNextRunAt(calculateNextRunAt(schedule));
			spark.setStatus(SparkState.SCHEDULED);
		}
		else {
			spark.setStatus(SparkState.PENDING);
		}

		sparkRepository.save(spark, sparkId);

		log.info("[SPARK] Created spark={} type={} for user={}", sparkId, effectiveType, userId);
		return spark;
	}

	/** Calculate the next run time from a cron expression. */
	public Instant calculateNextRunAt(String schedule) {
		CronExpression cron = CronExpression.parse(schedule);
		ZonedDateTime next = cron.next(ZonedDateTime.now(ZoneOffset.UTC));
		return next != null ? next.toInstant() : null;
	}

	/** Route a spark to the best available device based on user preferences. */
	public Optional<DeviceRegistration> routeSpark(String sparkId, String userId) {
		Optional<Spark> opt = sparkRepository.findById(sparkId);
		if (opt.isEmpty()) {
			return Optional.empty();
		}
		Spark spark = opt.get();
		spark.setStatus(SparkState.ROUTING);
		sparkRepository.save(spark, spark.getId());

		broadcastToUser(spark.getUserId(),
				Map.of("type", "spark_status", "sparkId", sparkId, "status", "ROUTING"));

		Optional<DeviceRegistration> device = findPreferredDevice(userId, spark.getType());

		if (device.isEmpty()) {
			device = deviceRoutingService.selectDevice(userId, null);
		}

		if (device.isPresent()) {
			spark.setDeviceId(device.get().getId());
			spark.setStatus(SparkState.EXECUTING);
			sparkRepository.save(spark, spark.getId());

			boolean dispatched = sparkDispatchService.dispatchSpark(spark);
			if (!dispatched) {
				spark.setStatus(SparkState.QUEUED);
				sparkRepository.save(spark, spark.getId());

				broadcastToUser(spark.getUserId(), Map.of("type", "spark_status", "sparkId", sparkId, "status",
						"QUEUED", "deviceName", device.get().getDeviceName()));

				log.warn("[SPARK] Device offline, spark {} queued for later dispatch", spark.getId());
			}
			else {
				broadcastToUser(spark.getUserId(), Map.of("type", "spark_status", "sparkId", sparkId, "status",
						"EXECUTING", "deviceName", device.get().getDeviceName()));

				log.info("[SPARK] Routed spark={} to device={}", sparkId, device.get().getId());
			}
		}
		else {
			spark.setStatus(SparkState.QUEUED);
			sparkRepository.save(spark, spark.getId());

			broadcastToUser(spark.getUserId(),
					Map.of("type", "spark_status", "sparkId", sparkId, "status", "QUEUED"));

			log.warn("[SPARK] No available device for spark={} user={}, queued for later dispatch", sparkId, userId);
		}

		return device;
	}

	/** Dispatch any queued sparks assigned to a device that just connected. */
	public void dispatchQueuedSparks(String deviceId, String userId) {
		List<Spark> queued = sparkRepository.findByUserIdAndStatus(userId, SparkState.QUEUED);
		for (Spark spark : queued) {
			if (deviceId.equals(spark.getDeviceId())) {
				log.info("[SPARK] Dispatching queued spark {} to device {}", spark.getId(), deviceId);
				spark.setStatus(SparkState.EXECUTING);
				sparkRepository.save(spark, spark.getId());
				sparkDispatchService.dispatchSpark(spark);

				broadcastToUser(userId, Map.of("type", "spark_status", "sparkId", spark.getId(), "status",
						"EXECUTING"));
			}
		}
	}

	/** Update spark progress from device. */
	public void onSparkProgress(String sparkId, SparkState status, long tokensDelta) {
		sparkRepository.findById(sparkId).ifPresent(spark -> {
			spark.setStatus(status);
			spark.setTotalTokens(spark.getTotalTokens() + tokensDelta);
			sparkRepository.save(spark, spark.getId());

			broadcastSparkUpdate(spark);
			broadcastToUser(spark.getUserId(), Map.of("type", "spark_progress", "sparkId", sparkId, "status",
					status.name(), "totalTokens", spark.getTotalTokens()));
		});
	}

	/** Create a checkpoint for user approval. */
	public Checkpoint onSparkCheckpoint(String sparkId, String tacticId, String title, String description,
			List<Map<String, Object>> findings, List<String> options) {
		String checkpointId = UUID.randomUUID().toString();

		Checkpoint checkpoint = new Checkpoint();
		checkpoint.setId(checkpointId);
		checkpoint.setSparkId(sparkId);
		checkpoint.setTacticId(tacticId);
		checkpoint.setTitle(title);
		checkpoint.setDescription(description);
		checkpoint.setFindings(findings);
		checkpoint.setOptions(options);
		checkpoint.setCreatedAt(Instant.now());
		checkpointRepository.save(checkpoint, checkpointId);

		sparkRepository.findById(sparkId).ifPresent(spark -> {
			spark.setStatus(SparkState.CHECKPOINT);
			sparkRepository.save(spark, spark.getId());
			broadcastSparkUpdate(spark);

			broadcastToUser(spark.getUserId(), Map.of("type", "spark_checkpoint", "sparkId", sparkId,
					"checkpointId", checkpointId, "title", title, "description", description));
		});

		log.info("[SPARK] Checkpoint created id={} for spark={}", checkpointId, sparkId);
		return checkpoint;
	}

	/** Store results and mark spark as completed. */
	public void onSparkCompleted(String sparkId, Map<String, Object> result, long totalTokens) {
		sparkRepository.findById(sparkId).ifPresent(spark -> {
			spark.setStatus(SparkState.COMPLETED);
			spark.setResult(result);
			spark.setTotalTokens(totalTokens);
			spark.setCompletedAt(Instant.now());
			sparkRepository.save(spark, spark.getId());

			broadcastSparkUpdate(spark);
			broadcastToUser(spark.getUserId(), Map.of("type", "spark_completed", "sparkId", sparkId,
					"totalTokens", totalTokens));

			log.info("[SPARK] Completed spark={} tokens={}", sparkId, totalTokens);
		});
	}

	/** Cancel a spark and cascade to all tactics. */
	public boolean cancelSpark(String sparkId, String userId) {
		Optional<Spark> opt = sparkRepository.findById(sparkId);
		if (opt.isEmpty()) {
			return false;
		}
		Spark spark = opt.get();
		if (!spark.getUserId().equals(userId)) {
			return false;
		}
		if (spark.getStatus() == SparkState.COMPLETED || spark.getStatus() == SparkState.CANCELLED) {
			return false;
		}

		spark.setStatus(SparkState.CANCELLED);
		spark.setCompletedAt(Instant.now());
		sparkRepository.save(spark, spark.getId());

		List<Tactic> tactics = tacticRepository.findBySparkId(sparkId);
		for (Tactic tactic : tactics) {
			if (tactic.getStatus() != TacticState.COMPLETED && tactic.getStatus() != TacticState.FAILED) {
				tactic.setStatus(TacticState.FAILED);
				tactic.setCompletedAt(Instant.now());
				tacticRepository.save(tactic, tactic.getId());
			}
		}

		broadcastSparkUpdate(spark);
		log.info("[SPARK] Cancelled spark={}", sparkId);
		return true;
	}

	/** Soft-delete a spark. */
	public boolean deleteSpark(String sparkId, String userId) {
		Optional<Spark> opt = sparkRepository.findById(sparkId);
		if (opt.isEmpty()) {
			return false;
		}
		Spark spark = opt.get();
		if (!spark.getUserId().equals(userId)) {
			return false;
		}
		spark.setActive(false);
		sparkRepository.save(spark, spark.getId());
		log.info("[SPARK] Soft-deleted spark={}", sparkId);
		return true;
	}

	/** Get a spark by ID, ensuring it belongs to the user. */
	public Optional<Spark> getSpark(String sparkId, String userId) {
		return sparkRepository.findById(sparkId).filter(s -> s.getUserId().equals(userId)).filter(Spark::isActive);
	}

	/** List active sparks for a user (paginated via limit). */
	public List<Spark> listSparks(String userId, int limit) {
		return sparkRepository.findRecentByUserId(userId, limit);
	}

	/** Get non-terminal sparks for the activity dashboard. */
	public List<Spark> getActiveSparks(String userId) {
		return sparkRepository.findActiveByUserId(userId);
	}

	/** Get recently completed/failed/cancelled sparks for the activity dashboard. */
	public List<Spark> getRecentSparks(String userId, int limit) {
		return sparkRepository.findRecentByUserId(userId, limit);
	}

	/** Get tactics for a spark. */
	public List<Tactic> getTactics(String sparkId) {
		return tacticRepository.findBySparkId(sparkId);
	}

	/** Mark a spark as failed. */
	public void onSparkFailed(String sparkId, String errorMessage) {
		sparkRepository.findById(sparkId).ifPresent(spark -> {
			spark.setStatus(SparkState.FAILED);
			spark.setResult(Map.of("error", errorMessage));
			spark.setCompletedAt(Instant.now());
			sparkRepository.save(spark, spark.getId());

			broadcastSparkUpdate(spark);
			broadcastToUser(spark.getUserId(),
					Map.of("type", "spark_failed", "sparkId", sparkId, "error", errorMessage));

			log.info("[SPARK] Failed spark={} error={}", sparkId, errorMessage);
		});
	}

	/** Mark a spark as EXECUTING for cloud (in-process) execution. */
	public void markRunning(String sparkId) {
		sparkRepository.findById(sparkId).ifPresent(spark -> {
			spark.setStatus(SparkState.EXECUTING);
			sparkRepository.save(spark, spark.getId());
			broadcastSparkUpdate(spark);
			log.info("[SPARK] Cloud execution started for spark={}", sparkId);
		});
	}

	/** Mark a spark as COMPLETED after cloud execution with cost estimation. */
	public void markCloudCompleted(String sparkId, long totalTokens, String modelId) {
		sparkRepository.findById(sparkId).ifPresent(spark -> {
			spark.setStatus(SparkState.COMPLETED);
			spark.setTotalTokens(totalTokens);
			spark.setEstimatedCost(estimateCost(totalTokens, modelId));
			spark.setCompletedAt(Instant.now());
			sparkRepository.save(spark, spark.getId());

			broadcastSparkUpdate(spark);
			broadcastToUser(spark.getUserId(), Map.of("type", "spark_completed", "sparkId", sparkId,
					"totalTokens", totalTokens));

			log.info("[SPARK] Cloud execution completed spark={} tokens={}", sparkId, totalTokens);
		});
	}

	/** Mark a spark as FAILED after cloud execution with error details and cost estimation. */
	public void markCloudFailed(String sparkId, String errorMessage, long totalTokens, String modelId) {
		sparkRepository.findById(sparkId).ifPresent(spark -> {
			spark.setStatus(SparkState.FAILED);
			spark.setResult(Map.of("error", errorMessage != null ? errorMessage : "Unknown error"));
			spark.setTotalTokens(totalTokens);
			spark.setEstimatedCost(estimateCost(totalTokens, modelId));
			spark.setCompletedAt(Instant.now());
			sparkRepository.save(spark, spark.getId());

			broadcastSparkUpdate(spark);
			broadcastToUser(spark.getUserId(),
					Map.of("type", "spark_failed", "sparkId", sparkId, "error",
							errorMessage != null ? errorMessage : "Unknown error"));

			log.info("[SPARK] Cloud execution failed spark={} error={}", sparkId, errorMessage);
		});
	}

	/** Sync a tactic reported by a device into Firestore. */
	public void syncTactic(String sparkId, String tacticId, String deviceId, String description, TacticState status,
			long tokenUsage) {
		Optional<Tactic> existing = tacticRepository.findById(tacticId);
		Tactic tactic;
		if (existing.isPresent()) {
			tactic = existing.get();
		}
		else {
			tactic = new Tactic();
			tactic.setId(tacticId);
			tactic.setSparkId(sparkId);
			tactic.setDeviceId(deviceId);
			tactic.setCreatedAt(Instant.now());
		}
		if (description != null) {
			tactic.setDescription(description);
		}
		tactic.setStatus(status);
		tactic.setTokenUsage(tokenUsage);
		if (status == TacticState.COMPLETED || status == TacticState.FAILED) {
			tactic.setCompletedAt(Instant.now());
		}
		tacticRepository.save(tactic, tactic.getId());
	}

	/**
	 * Get a spark by ID (internal use, no ownership check). Used by WebSocket handler which
	 * already verified device ownership via the authenticated session.
	 */
	public Optional<Spark> getSparkInternal(String sparkId) {
		return sparkRepository.findById(sparkId);
	}

	/** Get execution logs for a spark. */
	public List<ExecutionLog> getLogs(String sparkId) {
		return executionLogRepository.findBySparkId(sparkId);
	}

	/** Get all checkpoints for a user (pending ones first). */
	public List<Checkpoint> getCheckpointsForUser(String userId) {
		List<Spark> sparks = sparkRepository.findActiveByUserId(userId);
		return sparks.stream()
			.flatMap(s -> checkpointRepository.findBySparkId(s.getId()).stream())
			.sorted((a, b) -> {
				boolean aPending = a.getUserDecision() == null;
				boolean bPending = b.getUserDecision() == null;
				if (aPending != bPending) {
					return aPending ? -1 : 1;
				}
				return b.getCreatedAt().compareTo(a.getCreatedAt());
			})
			.toList();
	}

	/** Get a specific checkpoint. */
	public Optional<Checkpoint> getCheckpoint(String checkpointId) {
		return checkpointRepository.findById(checkpointId);
	}

	/** Decide on a checkpoint (approve/reject/modify). */
	public Optional<Checkpoint> decideCheckpoint(String checkpointId, String userId, CheckpointDecision decision,
			String feedback) {
		Optional<Checkpoint> opt = checkpointRepository.findById(checkpointId);
		if (opt.isEmpty()) {
			return Optional.empty();
		}
		Checkpoint checkpoint = opt.get();

		Optional<Spark> spark = sparkRepository.findById(checkpoint.getSparkId());
		if (spark.isEmpty() || !spark.get().getUserId().equals(userId)) {
			return Optional.empty();
		}

		checkpoint.setUserDecision(decision);
		checkpoint.setUserFeedback(feedback);
		checkpoint.setDecidedAt(Instant.now());
		checkpointRepository.save(checkpoint, checkpoint.getId());

		spark.ifPresent(s -> {
			s.setStatus(SparkState.EXECUTING);
			sparkRepository.save(s, s.getId());
			broadcastSparkUpdate(s);
		});

		log.info("[SPARK] Checkpoint {} decided: {} for spark={}", checkpointId, decision,
				checkpoint.getSparkId());
		return Optional.of(checkpoint);
	}

	private Optional<DeviceRegistration> findPreferredDevice(String userId, String sparkType) {
		if (sparkType == null) {
			return Optional.empty();
		}
		List<DevicePreference> preferences = devicePreferenceRepository.findAllByUserId(userId);
		Optional<DevicePreference> pref = preferences.stream()
			.filter(p -> sparkType.equals(p.getSparkType()))
			.findFirst();

		if (pref.isEmpty()) {
			return Optional.empty();
		}

		DevicePreference preference = pref.get();
		Optional<DeviceRegistration> device = deviceRoutingService.getOnlineDevice(preference.getPreferredDeviceId(),
				userId);

		if (device.isEmpty() && preference.getFallbackPolicy() == FallbackPolicy.ANY_AVAILABLE) {
			return deviceRoutingService.selectDevice(userId, null);
		}

		return device;
	}

	private void broadcastSparkUpdate(Spark spark) {
		activityBroadcaster.broadcastActivity(spark.getUserId(),
				Map.of("type", "spark_update", "sparkId", spark.getId(), "status", spark.getStatus().name()));
	}

	private void broadcastToUser(String userId, Map<String, Object> payload) {
		userBroadcaster.ifPresent(b -> b.broadcastToUser(userId, payload));
	}

	private BigDecimal estimateCost(long tokens, String modelId) {
		BigDecimal perMillionTokens;
		String model = modelId != null ? modelId : "";
		if (model.contains("opus")) {
			perMillionTokens = new BigDecimal("75.00");
		}
		else if (model.contains("sonnet")) {
			perMillionTokens = new BigDecimal("15.00");
		}
		else if (model.contains("haiku")) {
			perMillionTokens = new BigDecimal("1.25");
		}
		else {
			perMillionTokens = new BigDecimal("15.00");
		}
		return perMillionTokens.multiply(BigDecimal.valueOf(tokens))
			.divide(BigDecimal.valueOf(1_000_000), 4, RoundingMode.HALF_UP);
	}

}
