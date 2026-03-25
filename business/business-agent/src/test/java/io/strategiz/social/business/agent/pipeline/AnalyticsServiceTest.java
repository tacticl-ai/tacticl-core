package io.strategiz.social.business.agent.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineEvent;
import io.strategiz.social.data.entity.PipelineEventType;
import io.strategiz.social.data.entity.PipelineRun;
import io.strategiz.social.data.entity.PipelineStatus;
import io.strategiz.social.data.entity.PipelineTier;
import io.strategiz.social.data.entity.RoleResultSummary;
import io.strategiz.social.data.entity.RoleStatus;
import io.strategiz.social.data.repository.PipelineEventRepository;
import io.strategiz.social.data.repository.PipelineRunRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

	private static final String USER_ID = "user-analytics-001";

	@Mock
	private PipelineRunRepository pipelineRunRepository;

	@Mock
	private PipelineEventRepository pipelineEventRepository;

	private AnalyticsService analyticsService;

	@BeforeEach
	void setUp() {
		analyticsService = new AnalyticsService(pipelineRunRepository, pipelineEventRepository);
	}

	// --- Helpers ---

	private PipelineRun buildRun(String id, PipelineStatus status, String playbook, long tokens,
			BigDecimal cost) {
		PipelineRun run = new PipelineRun();
		run.setId(id);
		run.setUserId(USER_ID);
		run.setStatus(status);
		run.setPlaybook(playbook);
		run.setTotalTokens(tokens);
		run.setTotalCost(cost);
		run.setPipelineTier(PipelineTier.FULL_PDLC);
		run.setStartedAt(Instant.now());
		return run;
	}

	private PipelineRun buildRunWithRoles(String id, PipelineStatus status, Map<String, RoleResultSummary> roleResults,
			List<PdlcRole> activatedRoles, int reworkCount) {
		PipelineRun run = buildRun(id, status, "BUG_FIX", 5000, new BigDecimal("2.50"));
		run.setRoleResults(roleResults);
		run.setActivatedRoles(activatedRoles);
		run.setReworkCount(reworkCount);
		return run;
	}

	private RoleResultSummary buildRoleSummary(RoleStatus status, int iteration, long tokens, BigDecimal cost,
			long durationMs, String model) {
		RoleResultSummary summary = new RoleResultSummary();
		summary.setStatus(status);
		summary.setIteration(iteration);
		summary.setTokens(tokens);
		summary.setCost(cost);
		summary.setDurationMs(durationMs);
		summary.setModel(model);
		return summary;
	}

	private PipelineEvent buildEvent(String id, PipelineEventType type, PdlcRole role,
			Map<String, Object> metadata) {
		PipelineEvent event = new PipelineEvent();
		event.setId(id);
		event.setPipelineRunId("run-1");
		event.setUserId(USER_ID);
		event.setEventType(type);
		event.setRole(role);
		event.setMetadata(metadata);
		event.setTimestamp(Instant.now());
		return event;
	}

	// --- getKeyMetrics: empty data ---

	@Test
	void getKeyMetrics_emptyData_returnsZeros() {
		when(pipelineRunRepository.findAll()).thenReturn(List.of());

		AnalyticsService.KeyMetrics metrics = analyticsService.getKeyMetrics(null);

		assertEquals(0, metrics.totalPipelineRuns());
		assertEquals(0.0, metrics.successRate());
		assertEquals(0, metrics.activePipelines());
		assertEquals(0, metrics.pipelineBacklog());
		assertEquals(0, metrics.totalTokens());
		assertEquals(BigDecimal.ZERO, metrics.estimatedCost());
	}

	// --- getKeyMetrics: single pipeline ---

	@Test
	void getKeyMetrics_singleCompletedPipeline_returns100PercentSuccess() {
		PipelineRun run = buildRun("run-1", PipelineStatus.COMPLETED, "BUG_FIX", 10000,
				new BigDecimal("5.00"));
		when(pipelineRunRepository.findAll()).thenReturn(List.of(run));

		AnalyticsService.KeyMetrics metrics = analyticsService.getKeyMetrics(null);

		assertEquals(1, metrics.totalPipelineRuns());
		assertEquals(100.0, metrics.successRate());
		assertEquals(0, metrics.activePipelines());
		assertEquals(0, metrics.pipelineBacklog());
		assertEquals(10000, metrics.totalTokens());
		assertEquals(new BigDecimal("5.00"), metrics.estimatedCost());
	}

	// --- getKeyMetrics: multiple pipelines ---

	@Test
	void getKeyMetrics_multiplePipelines_aggregatesCorrectly() {
		PipelineRun completed = buildRun("run-1", PipelineStatus.COMPLETED, "BUG_FIX", 10000,
				new BigDecimal("5.00"));
		PipelineRun executing = buildRun("run-2", PipelineStatus.EXECUTING, "FEATURE", 3000,
				new BigDecimal("1.50"));
		PipelineRun failed = buildRun("run-3", PipelineStatus.FAILED, "BUG_FIX", 7000,
				new BigDecimal("3.00"));
		PipelineRun created = buildRun("run-4", PipelineStatus.CREATED, "FEATURE", 0,
				BigDecimal.ZERO);

		when(pipelineRunRepository.findAll()).thenReturn(List.of(completed, executing, failed, created));

		AnalyticsService.KeyMetrics metrics = analyticsService.getKeyMetrics(null);

		assertEquals(4, metrics.totalPipelineRuns());
		assertEquals(25.0, metrics.successRate()); // 1 completed out of 4
		assertEquals(1, metrics.activePipelines());
		assertEquals(1, metrics.pipelineBacklog()); // CREATED
		assertEquals(20000, metrics.totalTokens());
		assertEquals(new BigDecimal("9.50"), metrics.estimatedCost());
	}

	// --- getKeyMetrics: userId filtering ---

	@Test
	void getKeyMetrics_withUserId_filtersToUser() {
		PipelineRun run = buildRun("run-1", PipelineStatus.COMPLETED, "BUG_FIX", 10000,
				new BigDecimal("5.00"));
		when(pipelineRunRepository.findByUserId(USER_ID)).thenReturn(List.of(run));

		AnalyticsService.KeyMetrics metrics = analyticsService.getKeyMetrics(USER_ID);

		assertEquals(1, metrics.totalPipelineRuns());
		assertEquals(100.0, metrics.successRate());
	}

	// --- getRoleAnalytics ---

	@Test
	void getRoleAnalytics_emptyData_returnsEmptyList() {
		when(pipelineRunRepository.findAll()).thenReturn(List.of());

		List<AnalyticsService.RoleAnalytics> result = analyticsService.getRoleAnalytics(null);

		assertTrue(result.isEmpty());
	}

	@Test
	void getRoleAnalytics_groupsByRole() {
		RoleResultSummary researcher = buildRoleSummary(RoleStatus.COMPLETED, 1, 3000,
				new BigDecimal("1.00"), 15000, "claude-sonnet-4-20250514");
		RoleResultSummary implementer = buildRoleSummary(RoleStatus.COMPLETED, 2, 8000,
				new BigDecimal("4.00"), 45000, "claude-sonnet-4-20250514");

		PipelineRun run = buildRunWithRoles("run-1", PipelineStatus.COMPLETED,
				Map.of("RESEARCHER", researcher, "IMPLEMENTER", implementer),
				List.of(PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER), 1);

		when(pipelineRunRepository.findAll()).thenReturn(List.of(run));

		List<AnalyticsService.RoleAnalytics> result = analyticsService.getRoleAnalytics(null);

		assertEquals(2, result.size());

		AnalyticsService.RoleAnalytics researcherAnalytics = result.stream()
			.filter(r -> r.role() == PdlcRole.RESEARCHER)
			.findFirst()
			.orElseThrow();
		assertEquals(1, researcherAnalytics.runCount());
		assertEquals(3000, researcherAnalytics.totalTokens());
		assertEquals(new BigDecimal("1.00"), researcherAnalytics.totalCost());
		assertEquals(100.0, researcherAnalytics.firstPassRate()); // iteration=1, completed
		assertEquals(1.0, researcherAnalytics.avgReworkIterations());

		AnalyticsService.RoleAnalytics implementerAnalytics = result.stream()
			.filter(r -> r.role() == PdlcRole.IMPLEMENTER)
			.findFirst()
			.orElseThrow();
		assertEquals(1, implementerAnalytics.runCount());
		assertEquals(0.0, implementerAnalytics.firstPassRate()); // iteration=2, not first pass
		assertEquals(2.0, implementerAnalytics.avgReworkIterations());
	}

	// --- getReworkAnalytics ---

	@Test
	void getReworkAnalytics_emptyData_returnsZeros() {
		when(pipelineEventRepository.findAll()).thenReturn(List.of());
		when(pipelineRunRepository.findAll()).thenReturn(List.of());

		AnalyticsService.ReworkAnalytics result = analyticsService.getReworkAnalytics(null);

		assertEquals(0, result.totalReworkEvents());
		assertEquals(0.0, result.reworkRate());
		assertTrue(result.reworkByOrigin().isEmpty());
		assertEquals(0.0, result.firstPassRate());
	}

	@Test
	void getReworkAnalytics_tracksReworkOrigin() {
		PipelineEvent rework1 = buildEvent("e1", PipelineEventType.REWORK_TRIGGERED, PdlcRole.IMPLEMENTER,
				Map.of("rejectingRole", "REVIEWER"));
		PipelineEvent rework2 = buildEvent("e2", PipelineEventType.REWORK_TRIGGERED, PdlcRole.IMPLEMENTER,
				Map.of("rejectingRole", "REVIEWER"));
		PipelineEvent rework3 = buildEvent("e3", PipelineEventType.REWORK_TRIGGERED, PdlcRole.ARCHITECT,
				Map.of("rejectingRole", "TESTER"));
		PipelineEvent roleStarted1 = buildEvent("e4", PipelineEventType.ROLE_STARTED, PdlcRole.IMPLEMENTER,
				null);
		PipelineEvent roleStarted2 = buildEvent("e5", PipelineEventType.ROLE_STARTED, PdlcRole.REVIEWER,
				null);

		when(pipelineEventRepository.findAll()).thenReturn(
				List.of(rework1, rework2, rework3, roleStarted1, roleStarted2));

		PipelineRun completedNoRework = buildRunWithRoles("run-1", PipelineStatus.COMPLETED,
				Map.of(), List.of(), 0);
		PipelineRun completedWithRework = buildRunWithRoles("run-2", PipelineStatus.COMPLETED,
				Map.of(), List.of(), 2);
		when(pipelineRunRepository.findAll()).thenReturn(List.of(completedNoRework, completedWithRework));

		AnalyticsService.ReworkAnalytics result = analyticsService.getReworkAnalytics(null);

		assertEquals(3, result.totalReworkEvents());
		assertEquals(150.0, result.reworkRate()); // 3 reworks / 2 role starts
		assertEquals(2, result.reworkByOrigin().get(PdlcRole.REVIEWER));
		assertEquals(1, result.reworkByOrigin().get(PdlcRole.TESTER));
		assertEquals(50.0, result.firstPassRate()); // 1 out of 2 completed runs had no rework
	}

	// --- getPipelineFunnel ---

	@Test
	void getPipelineFunnel_emptyData_returnsAllRolesWithZeroCounts() {
		when(pipelineRunRepository.findAll()).thenReturn(List.of());

		List<AnalyticsService.FunnelStep> result = analyticsService.getPipelineFunnel(null);

		assertEquals(PdlcRole.values().length, result.size());
		result.forEach(step -> {
			assertEquals(0, step.count());
			assertEquals(0.0, step.dropOffPercentage());
		});
	}

	@Test
	void getPipelineFunnel_calculatesDropOff() {
		PipelineRun fullRun = buildRun("run-1", PipelineStatus.COMPLETED, "FULL_PDLC", 10000,
				new BigDecimal("5.00"));
		fullRun.setActivatedRoles(List.of(PdlcRole.PM, PdlcRole.RESEARCHER, PdlcRole.IMPLEMENTER));

		PipelineRun partialRun = buildRun("run-2", PipelineStatus.COMPLETED, "BUG_FIX", 5000,
				new BigDecimal("2.00"));
		partialRun.setActivatedRoles(List.of(PdlcRole.PM, PdlcRole.RESEARCHER));

		when(pipelineRunRepository.findAll()).thenReturn(List.of(fullRun, partialRun));

		List<AnalyticsService.FunnelStep> result = analyticsService.getPipelineFunnel(null);

		AnalyticsService.FunnelStep pmStep = result.stream()
			.filter(s -> s.role() == PdlcRole.PM)
			.findFirst()
			.orElseThrow();
		assertEquals(2, pmStep.count());
		assertEquals(0.0, pmStep.dropOffPercentage()); // All 2 reached PM

		AnalyticsService.FunnelStep researcherStep = result.stream()
			.filter(s -> s.role() == PdlcRole.RESEARCHER)
			.findFirst()
			.orElseThrow();
		assertEquals(2, researcherStep.count());

		AnalyticsService.FunnelStep implementerStep = result.stream()
			.filter(s -> s.role() == PdlcRole.IMPLEMENTER)
			.findFirst()
			.orElseThrow();
		assertEquals(1, implementerStep.count());
		assertEquals(50.0, implementerStep.dropOffPercentage()); // 1 out of 2
	}

	// --- getCostAnalytics ---

	@Test
	void getCostAnalytics_emptyData_returnsZeros() {
		when(pipelineRunRepository.findAll()).thenReturn(List.of());
		when(pipelineEventRepository.findAll()).thenReturn(List.of());

		AnalyticsService.CostAnalytics result = analyticsService.getCostAnalytics(null);

		assertTrue(result.costByRole().isEmpty());
		assertTrue(result.costByPlaybook().isEmpty());
		assertEquals(BigDecimal.ZERO, result.avgCostPerPipeline());
		assertEquals(0.0, result.costCeilingHitRate());
	}

	@Test
	void getCostAnalytics_aggregatesCostByRoleAndPlaybook() {
		RoleResultSummary researcher = buildRoleSummary(RoleStatus.COMPLETED, 1, 3000,
				new BigDecimal("1.50"), 10000, "claude-sonnet-4-20250514");
		RoleResultSummary implementer = buildRoleSummary(RoleStatus.COMPLETED, 1, 5000,
				new BigDecimal("3.00"), 30000, "claude-sonnet-4-20250514");

		PipelineRun run1 = buildRun("run-1", PipelineStatus.COMPLETED, "BUG_FIX", 8000,
				new BigDecimal("4.50"));
		run1.setRoleResults(Map.of("RESEARCHER", researcher, "IMPLEMENTER", implementer));

		PipelineRun run2 = buildRun("run-2", PipelineStatus.COMPLETED, "FEATURE", 5000,
				new BigDecimal("2.00"));
		run2.setRoleResults(Map.of());

		when(pipelineRunRepository.findAll()).thenReturn(List.of(run1, run2));
		when(pipelineEventRepository.findAll()).thenReturn(List.of());

		AnalyticsService.CostAnalytics result = analyticsService.getCostAnalytics(null);

		assertEquals(new BigDecimal("1.50"), result.costByRole().get(PdlcRole.RESEARCHER));
		assertEquals(new BigDecimal("3.00"), result.costByRole().get(PdlcRole.IMPLEMENTER));
		assertEquals(new BigDecimal("4.50"), result.costByPlaybook().get("BUG_FIX"));
		assertEquals(new BigDecimal("2.00"), result.costByPlaybook().get("FEATURE"));
		assertEquals(new BigDecimal("3.2500"), result.avgCostPerPipeline()); // 6.50 / 2
		assertEquals(0.0, result.costCeilingHitRate());
	}

	@Test
	void getCostAnalytics_tracksCostCeilingHitRate() {
		PipelineRun run = buildRun("run-1", PipelineStatus.COMPLETED, "BUG_FIX", 10000,
				new BigDecimal("50.00"));
		when(pipelineRunRepository.findAll()).thenReturn(List.of(run));

		PipelineEvent ceilingEvent = buildEvent("e1", PipelineEventType.COST_CEILING_REACHED, null, null);
		ceilingEvent.setPipelineRunId("run-1");
		when(pipelineEventRepository.findAll()).thenReturn(List.of(ceilingEvent));

		AnalyticsService.CostAnalytics result = analyticsService.getCostAnalytics(null);

		assertEquals(100.0, result.costCeilingHitRate());
	}

	// --- getModelDistribution ---

	@Test
	void getModelDistribution_countsModelsFromEvents() {
		PipelineEvent e1 = buildEvent("e1", PipelineEventType.ROLE_COMPLETED, PdlcRole.RESEARCHER,
				Map.of("model", "claude-sonnet-4-20250514"));
		PipelineEvent e2 = buildEvent("e2", PipelineEventType.ROLE_COMPLETED, PdlcRole.IMPLEMENTER,
				Map.of("model", "claude-sonnet-4-20250514"));
		PipelineEvent e3 = buildEvent("e3", PipelineEventType.ROLE_COMPLETED, PdlcRole.REVIEWER,
				Map.of("model", "gpt-4o"));
		PipelineEvent e4 = buildEvent("e4", PipelineEventType.ROLE_STARTED, PdlcRole.TESTER,
				Map.of("model", "should-be-ignored"));

		when(pipelineEventRepository.findAll()).thenReturn(List.of(e1, e2, e3, e4));

		Map<String, Long> result = analyticsService.getModelDistribution(null);

		assertEquals(2, result.get("claude-sonnet-4-20250514"));
		assertEquals(1, result.get("gpt-4o"));
		assertEquals(2, result.size()); // ROLE_STARTED ignored
	}

	// --- getPlaybookUsage ---

	@Test
	void getPlaybookUsage_groupsByPlaybook() {
		PipelineRun run1 = buildRun("run-1", PipelineStatus.COMPLETED, "BUG_FIX", 5000,
				new BigDecimal("2.00"));
		PipelineRun run2 = buildRun("run-2", PipelineStatus.EXECUTING, "BUG_FIX", 3000,
				new BigDecimal("1.00"));
		PipelineRun run3 = buildRun("run-3", PipelineStatus.COMPLETED, "FEATURE", 8000,
				new BigDecimal("4.00"));

		when(pipelineRunRepository.findAll()).thenReturn(List.of(run1, run2, run3));

		Map<String, Long> result = analyticsService.getPlaybookUsage(null);

		assertEquals(2, result.get("BUG_FIX"));
		assertEquals(1, result.get("FEATURE"));
	}

	@Test
	void getPlaybookUsage_handlesNullPlaybook() {
		PipelineRun run = buildRun("run-1", PipelineStatus.COMPLETED, null, 5000,
				new BigDecimal("2.00"));
		when(pipelineRunRepository.findAll()).thenReturn(List.of(run));

		Map<String, Long> result = analyticsService.getPlaybookUsage(null);

		assertEquals(1, result.get("UNKNOWN"));
	}

	// --- getDailyMetrics ---

	@Test
	void getDailyMetrics_returnsDaysEntries() {
		when(pipelineRunRepository.findAll()).thenReturn(List.of());

		List<AnalyticsService.DailyMetric> result = analyticsService.getDailyMetrics(null, 7);

		assertEquals(7, result.size());
	}

	@Test
	void getDailyMetrics_groupsRunsByDay() {
		Instant todayNoon = LocalDate.now(ZoneOffset.UTC).atTime(12, 0).toInstant(ZoneOffset.UTC);

		PipelineRun todayRun = buildRun("run-1", PipelineStatus.COMPLETED, "BUG_FIX", 10000,
				new BigDecimal("5.00"));
		todayRun.setStartedAt(todayNoon);

		PipelineRun todayFailed = buildRun("run-2", PipelineStatus.FAILED, "BUG_FIX", 3000,
				new BigDecimal("1.50"));
		todayFailed.setStartedAt(todayNoon.minusSeconds(3600));

		when(pipelineRunRepository.findAll()).thenReturn(List.of(todayRun, todayFailed));

		List<AnalyticsService.DailyMetric> result = analyticsService.getDailyMetrics(null, 7);

		// Last day should have today's data
		AnalyticsService.DailyMetric todayMetric = result.get(result.size() - 1);
		assertEquals(LocalDate.now(ZoneOffset.UTC), todayMetric.date());
		assertEquals(2, todayMetric.pipelineRuns());
		assertEquals(50.0, todayMetric.successRate()); // 1 completed out of 2
		assertEquals(13000, todayMetric.totalTokens());
		assertEquals(new BigDecimal("6.50"), todayMetric.totalCost());
	}

	// --- backlog includes AWAITING_CONFIRMATION ---

	@Test
	void getKeyMetrics_backlogIncludesAwaitingConfirmation() {
		PipelineRun awaiting = buildRun("run-1", PipelineStatus.AWAITING_CONFIRMATION, "BUG_FIX", 0,
				BigDecimal.ZERO);
		PipelineRun created = buildRun("run-2", PipelineStatus.CREATED, "FEATURE", 0,
				BigDecimal.ZERO);
		PipelineRun executing = buildRun("run-3", PipelineStatus.EXECUTING, "FEATURE", 0,
				BigDecimal.ZERO);

		when(pipelineRunRepository.findAll()).thenReturn(List.of(awaiting, created, executing));

		AnalyticsService.KeyMetrics metrics = analyticsService.getKeyMetrics(null);

		assertEquals(2, metrics.pipelineBacklog());
		assertEquals(1, metrics.activePipelines());
	}

}
