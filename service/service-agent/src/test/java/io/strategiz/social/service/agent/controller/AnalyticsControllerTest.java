package io.strategiz.social.service.agent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.business.agent.pipeline.AnalyticsService;
import io.strategiz.social.business.agent.pipeline.AnalyticsService.CostAnalytics;
import io.strategiz.social.business.agent.pipeline.AnalyticsService.DailyMetric;
import io.strategiz.social.business.agent.pipeline.AnalyticsService.FunnelStep;
import io.strategiz.social.business.agent.pipeline.AnalyticsService.KeyMetrics;
import io.strategiz.social.business.agent.pipeline.AnalyticsService.ReworkAnalytics;
import io.strategiz.social.business.agent.pipeline.AnalyticsService.RoleAnalytics;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.service.agent.dto.analytics.CostAnalyticsResponse;
import io.strategiz.social.service.agent.dto.analytics.DailyMetricResponse;
import io.strategiz.social.service.agent.dto.analytics.DashboardResponse;
import io.strategiz.social.service.agent.dto.analytics.FunnelStepResponse;
import io.strategiz.social.service.agent.dto.analytics.KeyMetricsResponse;
import io.strategiz.social.service.agent.dto.analytics.ReworkAnalyticsResponse;
import io.strategiz.social.service.agent.dto.analytics.RoleAnalyticsResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

	private static final String USER_ID = "user-123";

	@Mock
	private AnalyticsService analyticsService;

	private AnalyticsController controller;

	@BeforeEach
	void setUp() {
		controller = new AnalyticsController(analyticsService);
	}

	private AuthenticatedUser auth(String userId) {
		return AuthenticatedUser.builder().userId(userId).build();
	}

	private void stubAllAnalyticsMethods(String userId) {
		when(analyticsService.getKeyMetrics(userId))
			.thenReturn(new KeyMetrics(10, 80.0, 2, 1, 50000, new BigDecimal("25.00")));
		when(analyticsService.getRoleAnalytics(userId))
			.thenReturn(List.of(new RoleAnalytics(PdlcRole.IMPLEMENTER, 5, 30000, 20000,
					new BigDecimal("10.00"), 60.0, 1.4)));
		when(analyticsService.getReworkAnalytics(userId))
			.thenReturn(new ReworkAnalytics(3, 15.0, Map.of(PdlcRole.REVIEWER, 2), 85.0));
		when(analyticsService.getPipelineFunnel(userId))
			.thenReturn(List.of(new FunnelStep(PdlcRole.PM, 10, 0.0)));
		when(analyticsService.getCostAnalytics(userId))
			.thenReturn(new CostAnalytics(Map.of(PdlcRole.IMPLEMENTER, new BigDecimal("10.00")),
					Map.of("BUG_FIX", new BigDecimal("15.00")), new BigDecimal("2.50"), 5.0));
		when(analyticsService.getModelDistribution(userId))
			.thenReturn(Map.of("claude-sonnet-4-20250514", 8L));
		when(analyticsService.getPlaybookUsage(userId))
			.thenReturn(Map.of("BUG_FIX", 6L, "FEATURE", 4L));
		when(analyticsService.getDailyMetrics(eq(userId), eq(30)))
			.thenReturn(List.of(new DailyMetric(LocalDate.of(2026, 3, 25), 3, 66.7, 15000,
					new BigDecimal("7.50"))));
	}

	// --- Admin dashboard ---

	@Test
	void getDashboard_returnsAllMetrics() {
		stubAllAnalyticsMethods(null);

		ResponseEntity<DashboardResponse> response = controller.getDashboard(30, auth(USER_ID));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		DashboardResponse dashboard = response.getBody();
		assertNotNull(dashboard);
		assertNotNull(dashboard.getKeyMetrics());
		assertNotNull(dashboard.getRoleAnalytics());
		assertNotNull(dashboard.getReworkAnalytics());
		assertNotNull(dashboard.getFunnel());
		assertNotNull(dashboard.getCostAnalytics());
		assertNotNull(dashboard.getModelDistribution());
		assertNotNull(dashboard.getPlaybookUsage());
		assertNotNull(dashboard.getDailyMetrics());

		assertEquals(10, dashboard.getKeyMetrics().getTotalPipelineRuns());
		assertEquals(80.0, dashboard.getKeyMetrics().getSuccessRate());
	}

	@Test
	void getDashboard_passesNullUserIdForAdmin() {
		stubAllAnalyticsMethods(null);

		controller.getDashboard(30, auth(USER_ID));

		verify(analyticsService).getKeyMetrics(null);
		verify(analyticsService).getRoleAnalytics(null);
		verify(analyticsService).getReworkAnalytics(null);
	}

	// --- Admin key-metrics ---

	@Test
	void getKeyMetrics_returnsCorrectStructure() {
		when(analyticsService.getKeyMetrics(null))
			.thenReturn(new KeyMetrics(10, 80.0, 2, 1, 50000, new BigDecimal("25.00")));

		ResponseEntity<KeyMetricsResponse> response = controller.getKeyMetrics(auth(USER_ID));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(10, response.getBody().getTotalPipelineRuns());
		assertEquals(80.0, response.getBody().getSuccessRate());
		assertEquals(2, response.getBody().getActivePipelines());
		assertEquals(1, response.getBody().getPipelineBacklog());
		assertEquals(50000, response.getBody().getTotalTokens());
		assertEquals(new BigDecimal("25.00"), response.getBody().getEstimatedCost());
	}

	// --- Admin roles ---

	@Test
	void getRoleAnalytics_returnsPerRoleData() {
		when(analyticsService.getRoleAnalytics(null))
			.thenReturn(List.of(
					new RoleAnalytics(PdlcRole.RESEARCHER, 5, 15000, 10000,
							new BigDecimal("5.00"), 80.0, 1.2),
					new RoleAnalytics(PdlcRole.IMPLEMENTER, 5, 30000, 20000,
							new BigDecimal("10.00"), 60.0, 1.4)));

		ResponseEntity<List<RoleAnalyticsResponse>> response = controller.getRoleAnalytics(auth(USER_ID));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(2, response.getBody().size());
		assertEquals(PdlcRole.RESEARCHER, response.getBody().get(0).getRole());
		assertEquals(PdlcRole.IMPLEMENTER, response.getBody().get(1).getRole());
	}

	// --- Admin rework ---

	@Test
	void getReworkAnalytics_returnsReworkData() {
		when(analyticsService.getReworkAnalytics(null))
			.thenReturn(new ReworkAnalytics(3, 15.0, Map.of(PdlcRole.REVIEWER, 2, PdlcRole.TESTER, 1), 85.0));

		ResponseEntity<ReworkAnalyticsResponse> response = controller.getReworkAnalytics(auth(USER_ID));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(3, response.getBody().getTotalReworkEvents());
		assertEquals(15.0, response.getBody().getReworkRate());
		assertEquals(2, response.getBody().getReworkByOrigin().get(PdlcRole.REVIEWER));
	}

	// --- Admin funnel ---

	@Test
	void getFunnel_returnsFunnelSteps() {
		when(analyticsService.getPipelineFunnel(null))
			.thenReturn(List.of(
					new FunnelStep(PdlcRole.PM, 10, 0.0),
					new FunnelStep(PdlcRole.IMPLEMENTER, 7, 30.0)));

		ResponseEntity<List<FunnelStepResponse>> response = controller.getFunnel(auth(USER_ID));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(2, response.getBody().size());
		assertEquals(10, response.getBody().get(0).getCount());
		assertEquals(30.0, response.getBody().get(1).getDropOffPercentage());
	}

	// --- Admin cost ---

	@Test
	void getCostAnalytics_returnsCostBreakdown() {
		when(analyticsService.getCostAnalytics(null))
			.thenReturn(new CostAnalytics(Map.of(PdlcRole.IMPLEMENTER, new BigDecimal("10.00")),
					Map.of("BUG_FIX", new BigDecimal("15.00")), new BigDecimal("2.50"), 5.0));

		ResponseEntity<CostAnalyticsResponse> response = controller.getCostAnalytics(auth(USER_ID));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(new BigDecimal("10.00"), response.getBody().getCostByRole().get(PdlcRole.IMPLEMENTER));
		assertEquals(new BigDecimal("15.00"), response.getBody().getCostByPlaybook().get("BUG_FIX"));
		assertEquals(new BigDecimal("2.50"), response.getBody().getAvgCostPerPipeline());
		assertEquals(5.0, response.getBody().getCostCeilingHitRate());
	}

	// --- Admin models ---

	@Test
	void getModelDistribution_returnsModelCounts() {
		when(analyticsService.getModelDistribution(null))
			.thenReturn(Map.of("claude-sonnet-4-20250514", 8L, "gpt-4o", 2L));

		ResponseEntity<Map<String, Long>> response = controller.getModelDistribution(auth(USER_ID));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(8L, response.getBody().get("claude-sonnet-4-20250514"));
		assertEquals(2L, response.getBody().get("gpt-4o"));
	}

	// --- Admin playbooks ---

	@Test
	void getPlaybookUsage_returnsPlaybookCounts() {
		when(analyticsService.getPlaybookUsage(null))
			.thenReturn(Map.of("BUG_FIX", 6L, "FEATURE", 4L));

		ResponseEntity<Map<String, Long>> response = controller.getPlaybookUsage(auth(USER_ID));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(6L, response.getBody().get("BUG_FIX"));
		assertEquals(4L, response.getBody().get("FEATURE"));
	}

	// --- Admin daily ---

	@Test
	void getDailyMetrics_returnsDailyData() {
		when(analyticsService.getDailyMetrics(null, 7))
			.thenReturn(List.of(
					new DailyMetric(LocalDate.of(2026, 3, 25), 3, 66.7, 15000,
							new BigDecimal("7.50"))));

		ResponseEntity<List<DailyMetricResponse>> response = controller.getDailyMetrics(7, auth(USER_ID));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(1, response.getBody().size());
		assertEquals(LocalDate.of(2026, 3, 25), response.getBody().get(0).getDate());
		assertEquals(3, response.getBody().get(0).getPipelineRuns());
	}

	// --- User-scoped endpoint ---

	@Test
	void getUserDashboard_passesAuthenticatedUserId() {
		stubAllAnalyticsMethods(USER_ID);

		ResponseEntity<DashboardResponse> response = controller.getUserDashboard(30, auth(USER_ID));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());

		verify(analyticsService).getKeyMetrics(USER_ID);
		verify(analyticsService).getRoleAnalytics(USER_ID);
		verify(analyticsService).getReworkAnalytics(USER_ID);
		verify(analyticsService).getPipelineFunnel(USER_ID);
		verify(analyticsService).getCostAnalytics(USER_ID);
		verify(analyticsService).getModelDistribution(USER_ID);
		verify(analyticsService).getPlaybookUsage(USER_ID);
		verify(analyticsService).getDailyMetrics(USER_ID, 30);
	}

	@Test
	void getUserDashboard_returnsCorrectStructure() {
		stubAllAnalyticsMethods(USER_ID);

		ResponseEntity<DashboardResponse> response = controller.getUserDashboard(30, auth(USER_ID));

		DashboardResponse dashboard = response.getBody();
		assertNotNull(dashboard);
		assertEquals(10, dashboard.getKeyMetrics().getTotalPipelineRuns());
		assertEquals(1, dashboard.getRoleAnalytics().size());
		assertEquals(PdlcRole.IMPLEMENTER, dashboard.getRoleAnalytics().get(0).getRole());
		assertEquals(3, dashboard.getReworkAnalytics().getTotalReworkEvents());
		assertEquals(1, dashboard.getFunnel().size());
		assertNotNull(dashboard.getCostAnalytics());
		assertEquals(1, dashboard.getModelDistribution().size());
		assertEquals(2, dashboard.getPlaybookUsage().size());
		assertEquals(1, dashboard.getDailyMetrics().size());
	}

}
