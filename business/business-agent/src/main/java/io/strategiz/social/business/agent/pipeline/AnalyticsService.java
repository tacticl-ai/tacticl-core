package io.strategiz.social.business.agent.pipeline;

import io.cidadel.service.base.BaseService;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineEvent;
import io.strategiz.social.data.entity.PipelineEventType;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.PipelineStatus;
import io.strategiz.social.data.entity.RoleResultSummary;
import io.strategiz.social.data.entity.RoleStatus;
import io.strategiz.social.data.repository.PipelineEventRepository;
import io.strategiz.social.data.repository.PipelineRunRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Aggregation service for the Admin Console analytics dashboard. Computes key metrics,
 * role proficiency, rework tracking, pipeline funnel, cost analysis, and time series
 * from pipeline run and event data.
 *
 * <p>All methods accept a nullable {@code userId}. If null, metrics are aggregated
 * across all users (admin view). If non-null, results are filtered to that user only.
 */
@Service
public class AnalyticsService extends BaseService {

	private static final String MODULE_NAME = "business-agent";

	private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

	private final PipelineRunRepository pipelineRunRepository;

	private final PipelineEventRepository pipelineEventRepository;

	public AnalyticsService(PipelineRunRepository pipelineRunRepository,
			PipelineEventRepository pipelineEventRepository) {
		this.pipelineRunRepository = pipelineRunRepository;
		this.pipelineEventRepository = pipelineEventRepository;
	}

	@Override
	protected String getModuleName() {
		return MODULE_NAME;
	}

	// --- Key Metrics ---

	/** Compute top-level dashboard key metrics. */
	public KeyMetrics getKeyMetrics(String userId) {
		List<PipelineRun> runs = loadRuns(userId);

		long total = runs.size();
		long completed = runs.stream().filter(r -> r.getStatus() == PipelineStatus.COMPLETED).count();
		double successRate = total > 0 ? (completed * 100.0) / total : 0.0;
		long active = runs.stream().filter(r -> r.getStatus() == PipelineStatus.EXECUTING).count();
		long backlog = runs.stream()
			.filter(r -> r.getStatus() == PipelineStatus.CREATED || r.getStatus() == PipelineStatus.AWAITING_CONFIRMATION)
			.count();
		long totalTokens = runs.stream().mapToLong(PipelineRun::getTotalTokens).sum();
		BigDecimal estimatedCost = runs.stream()
			.map(r -> r.getTotalCost() != null ? r.getTotalCost() : BigDecimal.ZERO)
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		return new KeyMetrics(total, successRate, active, backlog, totalTokens, estimatedCost);
	}

	// --- Role Analytics ---

	/** Compute per-role proficiency analytics from role results embedded in pipeline runs. */
	public List<RoleAnalytics> getRoleAnalytics(String userId) {
		List<PipelineRun> runs = loadRuns(userId);

		Map<PdlcRole, List<RoleResultSummary>> resultsByRole = new EnumMap<>(PdlcRole.class);

		for (PipelineRun run : runs) {
			if (run.getRoleResults() == null) {
				continue;
			}
			for (Map.Entry<String, RoleResultSummary> entry : run.getRoleResults().entrySet()) {
				try {
					PdlcRole role = PdlcRole.valueOf(entry.getKey());
					resultsByRole.computeIfAbsent(role, k -> new java.util.ArrayList<>()).add(entry.getValue());
				}
				catch (IllegalArgumentException ignored) {
					// Skip unknown role keys
				}
			}
		}

		return resultsByRole.entrySet().stream().map(entry -> {
			PdlcRole role = entry.getKey();
			List<RoleResultSummary> summaries = entry.getValue();

			int runCount = summaries.size();
			long avgDuration = runCount > 0
					? summaries.stream().mapToLong(RoleResultSummary::getDurationMs).sum() / runCount : 0;
			long totalTokens = summaries.stream().mapToLong(RoleResultSummary::getTokens).sum();
			BigDecimal totalCost = summaries.stream()
				.map(s -> s.getCost() != null ? s.getCost() : BigDecimal.ZERO)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
			long firstPassCompleted = summaries.stream()
				.filter(s -> s.getIteration() == 1 && s.getStatus() == RoleStatus.COMPLETED)
				.count();
			double firstPassRate = runCount > 0 ? (firstPassCompleted * 100.0) / runCount : 0.0;
			double avgReworkIterations = runCount > 0
					? summaries.stream().mapToInt(RoleResultSummary::getIteration).average().orElse(1.0) : 1.0;

			return new RoleAnalytics(role, runCount, avgDuration, totalTokens, totalCost, firstPassRate,
					avgReworkIterations);
		}).toList();
	}

	// --- Rework Analytics ---

	/** Compute rework tracking analytics from pipeline events. */
	public ReworkAnalytics getReworkAnalytics(String userId) {
		List<PipelineEvent> events = loadEvents(userId);
		List<PipelineRun> runs = loadRuns(userId);

		long totalReworkEvents = events.stream()
			.filter(e -> e.getEventType() == PipelineEventType.REWORK_TRIGGERED)
			.count();

		// Total role executions = ROLE_STARTED events
		long totalRoleExecutions = events.stream()
			.filter(e -> e.getEventType() == PipelineEventType.ROLE_STARTED)
			.count();
		double reworkRate = totalRoleExecutions > 0 ? (totalReworkEvents * 100.0) / totalRoleExecutions : 0.0;

		// Rework by origin: which role triggered the rework (rejectingRole in metadata)
		Map<PdlcRole, Integer> reworkByOrigin = new EnumMap<>(PdlcRole.class);
		events.stream()
			.filter(e -> e.getEventType() == PipelineEventType.REWORK_TRIGGERED)
			.forEach(e -> {
				PdlcRole rejectingRole = extractRejectingRole(e);
				if (rejectingRole != null) {
					reworkByOrigin.merge(rejectingRole, 1, Integer::sum);
				}
			});

		// First pass rate: pipelines completed without any rework
		long totalRuns = runs.size();
		long noReworkRuns = runs.stream().filter(r -> r.getReworkCount() == 0
				&& r.getStatus() == PipelineStatus.COMPLETED).count();
		long completedRuns = runs.stream().filter(r -> r.getStatus() == PipelineStatus.COMPLETED).count();
		double firstPassRate = completedRuns > 0 ? (noReworkRuns * 100.0) / completedRuns : 0.0;

		return new ReworkAnalytics(totalReworkEvents, reworkRate, reworkByOrigin, firstPassRate);
	}

	// --- Pipeline Funnel ---

	/** Compute the pipeline funnel showing how many pipelines reached each PDLC role stage. */
	public List<FunnelStep> getPipelineFunnel(String userId) {
		List<PipelineRun> runs = loadRuns(userId);
		long totalRuns = runs.size();

		// Use FULL_PDLC role order
		PdlcRole[] fullOrder = PdlcRole.values();

		return Arrays.stream(fullOrder).map(role -> {
			long reached = runs.stream()
				.filter(r -> r.getActivatedRoles() != null && r.getActivatedRoles().contains(role))
				.count();
			double dropOff = totalRuns > 0 ? ((totalRuns - reached) * 100.0) / totalRuns : 0.0;
			return new FunnelStep(role, reached, dropOff);
		}).toList();
	}

	// --- Cost Analytics ---

	/** Compute cost analytics broken down by role, playbook, and averages. */
	public CostAnalytics getCostAnalytics(String userId) {
		List<PipelineRun> runs = loadRuns(userId);
		List<PipelineEvent> events = loadEvents(userId);

		// Cost by role
		Map<PdlcRole, BigDecimal> costByRole = new EnumMap<>(PdlcRole.class);
		for (PipelineRun run : runs) {
			if (run.getRoleResults() == null) {
				continue;
			}
			for (Map.Entry<String, RoleResultSummary> entry : run.getRoleResults().entrySet()) {
				try {
					PdlcRole role = PdlcRole.valueOf(entry.getKey());
					BigDecimal cost = entry.getValue().getCost() != null ? entry.getValue().getCost() : BigDecimal.ZERO;
					costByRole.merge(role, cost, BigDecimal::add);
				}
				catch (IllegalArgumentException ignored) {
				}
			}
		}

		// Cost by playbook
		Map<String, BigDecimal> costByPlaybook = new HashMap<>();
		for (PipelineRun run : runs) {
			String playbook = run.getPlaybook() != null ? run.getPlaybook() : "UNKNOWN";
			BigDecimal cost = run.getTotalCost() != null ? run.getTotalCost() : BigDecimal.ZERO;
			costByPlaybook.merge(playbook, cost, BigDecimal::add);
		}

		// Average cost per pipeline
		BigDecimal totalCost = runs.stream()
			.map(r -> r.getTotalCost() != null ? r.getTotalCost() : BigDecimal.ZERO)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal avgCost = !runs.isEmpty()
				? totalCost.divide(BigDecimal.valueOf(runs.size()), 4, RoundingMode.HALF_UP)
				: BigDecimal.ZERO;

		// Cost ceiling hit rate
		long ceilingHits = events.stream()
			.filter(e -> e.getEventType() == PipelineEventType.COST_CEILING_REACHED)
			.map(PipelineEvent::getPipelineRunId)
			.distinct()
			.count();
		double ceilingHitRate = !runs.isEmpty() ? (ceilingHits * 100.0) / runs.size() : 0.0;

		return new CostAnalytics(costByRole, costByPlaybook, avgCost, ceilingHitRate);
	}

	// --- Model Distribution ---

	/** Count role executions by model from ROLE_COMPLETED event metadata. */
	public Map<String, Long> getModelDistribution(String userId) {
		List<PipelineEvent> events = loadEvents(userId);

		return events.stream()
			.filter(e -> e.getEventType() == PipelineEventType.ROLE_COMPLETED)
			.filter(e -> e.getMetadata() != null && e.getMetadata().containsKey("model"))
			.collect(Collectors.groupingBy(
					e -> String.valueOf(e.getMetadata().get("model")),
					Collectors.counting()));
	}

	// --- Playbook Usage ---

	/** Count pipeline runs by playbook name. */
	public Map<String, Long> getPlaybookUsage(String userId) {
		List<PipelineRun> runs = loadRuns(userId);

		return runs.stream()
			.collect(Collectors.groupingBy(
					r -> r.getPlaybook() != null ? r.getPlaybook() : "UNKNOWN",
					Collectors.counting()));
	}

	// --- Daily Metrics ---

	/** Compute daily aggregated metrics for the last N days. */
	public List<DailyMetric> getDailyMetrics(String userId, int days) {
		List<PipelineRun> runs = loadRuns(userId);

		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		LocalDate start = today.minusDays(days - 1);

		// Group runs by day
		Map<LocalDate, List<PipelineRun>> runsByDay = runs.stream()
			.filter(r -> r.getStartedAt() != null)
			.collect(Collectors.groupingBy(
					r -> r.getStartedAt().atZone(ZoneOffset.UTC).toLocalDate()));

		return start.datesUntil(today.plusDays(1)).map(date -> {
			List<PipelineRun> dayRuns = runsByDay.getOrDefault(date, List.of());
			long total = dayRuns.size();
			long completed = dayRuns.stream().filter(r -> r.getStatus() == PipelineStatus.COMPLETED).count();
			double successRate = total > 0 ? (completed * 100.0) / total : 0.0;
			long totalTokens = dayRuns.stream().mapToLong(PipelineRun::getTotalTokens).sum();
			BigDecimal totalCost = dayRuns.stream()
				.map(r -> r.getTotalCost() != null ? r.getTotalCost() : BigDecimal.ZERO)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
			return new DailyMetric(date, total, successRate, totalTokens, totalCost);
		}).toList();
	}

	// --- Internal helpers ---

	private List<PipelineRun> loadRuns(String userId) {
		if (userId != null) {
			return pipelineRunRepository.findByUserId(userId);
		}
		return pipelineRunRepository.findAll();
	}

	private List<PipelineEvent> loadEvents(String userId) {
		if (userId != null) {
			return pipelineEventRepository.findByUserId(userId);
		}
		return pipelineEventRepository.findAll();
	}

	private PdlcRole extractRejectingRole(PipelineEvent event) {
		if (event.getMetadata() == null) {
			return null;
		}
		Object rejectingRole = event.getMetadata().get("rejectingRole");
		if (rejectingRole == null) {
			return null;
		}
		try {
			return PdlcRole.valueOf(String.valueOf(rejectingRole));
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	// --- Record types for aggregation results ---

	/** Top-level dashboard key metrics. */
	public record KeyMetrics(long totalPipelineRuns, double successRate, long activePipelines,
			long pipelineBacklog, long totalTokens, BigDecimal estimatedCost) {
	}

	/** Per-role proficiency analytics. */
	public record RoleAnalytics(PdlcRole role, int runCount, long avgDurationMs, long totalTokens,
			BigDecimal totalCost, double firstPassRate, double avgReworkIterations) {
	}

	/** Rework tracking analytics. */
	public record ReworkAnalytics(long totalReworkEvents, double reworkRate,
			Map<PdlcRole, Integer> reworkByOrigin, double firstPassRate) {
	}

	/** Single step in the pipeline funnel. */
	public record FunnelStep(PdlcRole role, long count, double dropOffPercentage) {
	}

	/** Cost analytics broken down by role, playbook, and averages. */
	public record CostAnalytics(Map<PdlcRole, BigDecimal> costByRole,
			Map<String, BigDecimal> costByPlaybook, BigDecimal avgCostPerPipeline, double costCeilingHitRate) {
	}

	/** Daily aggregated metrics for time series. */
	public record DailyMetric(LocalDate date, long pipelineRuns, double successRate, long totalTokens,
			BigDecimal totalCost) {
	}

}
