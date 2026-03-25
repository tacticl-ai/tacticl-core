package io.strategiz.social.service.agent.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.annotation.RequireScope;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.business.agent.pipeline.AnalyticsService;
import io.strategiz.social.service.agent.dto.analytics.CostAnalyticsResponse;
import io.strategiz.social.service.agent.dto.analytics.DailyMetricResponse;
import io.strategiz.social.service.agent.dto.analytics.DashboardResponse;
import io.strategiz.social.service.agent.dto.analytics.FunnelStepResponse;
import io.strategiz.social.service.agent.dto.analytics.KeyMetricsResponse;
import io.strategiz.social.service.agent.dto.analytics.ReworkAnalyticsResponse;
import io.strategiz.social.service.agent.dto.analytics.RoleAnalyticsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller exposing analytics endpoints for the Admin Console dashboard and user-scoped analytics. */
@RestController
@Tag(name = "Analytics", description = "Pipeline analytics and dashboard metrics")
public class AnalyticsController {

	private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);

	private static final int DEFAULT_DAILY_DAYS = 30;

	private final AnalyticsService analyticsService;

	public AnalyticsController(AnalyticsService analyticsService) {
		this.analyticsService = analyticsService;
	}

	// --- Admin endpoints (global view) ---

	@GetMapping("/api/admin/analytics/dashboard")
	@RequireAuth
	@RequireScope("admin")
	@Operation(summary = "Get full analytics dashboard",
			description = "Returns all analytics metrics combined for the admin dashboard. Global view across all users.")
	public ResponseEntity<DashboardResponse> getDashboard(
			@RequestParam(defaultValue = "30") int days,
			@AuthUser AuthenticatedUser user) {
		log.debug("Admin dashboard requested by user={} days={}", user.getUserId(), days);
		return ResponseEntity.ok(buildDashboard(null, days));
	}

	@GetMapping("/api/admin/analytics/key-metrics")
	@RequireAuth
	@RequireScope("admin")
	@Operation(summary = "Get key metrics", description = "Top-level pipeline key metrics across all users.")
	public ResponseEntity<KeyMetricsResponse> getKeyMetrics(@AuthUser AuthenticatedUser user) {
		log.debug("Admin key-metrics requested by user={}", user.getUserId());
		return ResponseEntity.ok(KeyMetricsResponse.from(analyticsService.getKeyMetrics(null)));
	}

	@GetMapping("/api/admin/analytics/roles")
	@RequireAuth
	@RequireScope("admin")
	@Operation(summary = "Get role analytics", description = "Per-role proficiency analytics across all users.")
	public ResponseEntity<List<RoleAnalyticsResponse>> getRoleAnalytics(@AuthUser AuthenticatedUser user) {
		log.debug("Admin role-analytics requested by user={}", user.getUserId());
		List<RoleAnalyticsResponse> response = analyticsService.getRoleAnalytics(null).stream()
			.map(RoleAnalyticsResponse::from)
			.toList();
		return ResponseEntity.ok(response);
	}

	@GetMapping("/api/admin/analytics/rework")
	@RequireAuth
	@RequireScope("admin")
	@Operation(summary = "Get rework analytics", description = "Rework tracking metrics across all users.")
	public ResponseEntity<ReworkAnalyticsResponse> getReworkAnalytics(@AuthUser AuthenticatedUser user) {
		log.debug("Admin rework-analytics requested by user={}", user.getUserId());
		return ResponseEntity.ok(ReworkAnalyticsResponse.from(analyticsService.getReworkAnalytics(null)));
	}

	@GetMapping("/api/admin/analytics/funnel")
	@RequireAuth
	@RequireScope("admin")
	@Operation(summary = "Get pipeline funnel", description = "Pipeline funnel showing drop-off per PDLC role stage.")
	public ResponseEntity<List<FunnelStepResponse>> getFunnel(@AuthUser AuthenticatedUser user) {
		log.debug("Admin funnel requested by user={}", user.getUserId());
		List<FunnelStepResponse> response = analyticsService.getPipelineFunnel(null).stream()
			.map(FunnelStepResponse::from)
			.toList();
		return ResponseEntity.ok(response);
	}

	@GetMapping("/api/admin/analytics/cost")
	@RequireAuth
	@RequireScope("admin")
	@Operation(summary = "Get cost analytics", description = "Cost breakdown by role, playbook, and averages.")
	public ResponseEntity<CostAnalyticsResponse> getCostAnalytics(@AuthUser AuthenticatedUser user) {
		log.debug("Admin cost-analytics requested by user={}", user.getUserId());
		return ResponseEntity.ok(CostAnalyticsResponse.from(analyticsService.getCostAnalytics(null)));
	}

	@GetMapping("/api/admin/analytics/models")
	@RequireAuth
	@RequireScope("admin")
	@Operation(summary = "Get model distribution", description = "Count of role executions by AI model.")
	public ResponseEntity<Map<String, Long>> getModelDistribution(@AuthUser AuthenticatedUser user) {
		log.debug("Admin model-distribution requested by user={}", user.getUserId());
		return ResponseEntity.ok(analyticsService.getModelDistribution(null));
	}

	@GetMapping("/api/admin/analytics/playbooks")
	@RequireAuth
	@RequireScope("admin")
	@Operation(summary = "Get playbook usage", description = "Count of pipeline runs by playbook name.")
	public ResponseEntity<Map<String, Long>> getPlaybookUsage(@AuthUser AuthenticatedUser user) {
		log.debug("Admin playbook-usage requested by user={}", user.getUserId());
		return ResponseEntity.ok(analyticsService.getPlaybookUsage(null));
	}

	@GetMapping("/api/admin/analytics/daily")
	@RequireAuth
	@RequireScope("admin")
	@Operation(summary = "Get daily metrics", description = "Daily aggregated metrics for time series charts.")
	public ResponseEntity<List<DailyMetricResponse>> getDailyMetrics(
			@RequestParam(defaultValue = "30") int days,
			@AuthUser AuthenticatedUser user) {
		log.debug("Admin daily-metrics requested by user={} days={}", user.getUserId(), days);
		List<DailyMetricResponse> response = analyticsService.getDailyMetrics(null, days).stream()
			.map(DailyMetricResponse::from)
			.toList();
		return ResponseEntity.ok(response);
	}

	// --- User-scoped endpoint ---

	@GetMapping("/api/sparks/analytics")
	@RequireAuth
	@Operation(summary = "Get user analytics dashboard",
			description = "Returns analytics dashboard scoped to the authenticated user's own pipeline data.")
	public ResponseEntity<DashboardResponse> getUserDashboard(
			@RequestParam(defaultValue = "30") int days,
			@AuthUser AuthenticatedUser user) {
		log.debug("User dashboard requested by user={} days={}", user.getUserId(), days);
		return ResponseEntity.ok(buildDashboard(user.getUserId(), days));
	}

	// --- Internal ---

	private DashboardResponse buildDashboard(String userId, int days) {
		DashboardResponse dashboard = new DashboardResponse();
		dashboard.setKeyMetrics(KeyMetricsResponse.from(analyticsService.getKeyMetrics(userId)));
		dashboard.setRoleAnalytics(analyticsService.getRoleAnalytics(userId).stream()
			.map(RoleAnalyticsResponse::from)
			.toList());
		dashboard.setReworkAnalytics(ReworkAnalyticsResponse.from(analyticsService.getReworkAnalytics(userId)));
		dashboard.setFunnel(analyticsService.getPipelineFunnel(userId).stream()
			.map(FunnelStepResponse::from)
			.toList());
		dashboard.setCostAnalytics(CostAnalyticsResponse.from(analyticsService.getCostAnalytics(userId)));
		dashboard.setModelDistribution(analyticsService.getModelDistribution(userId));
		dashboard.setPlaybookUsage(analyticsService.getPlaybookUsage(userId));
		dashboard.setDailyMetrics(analyticsService.getDailyMetrics(userId, days).stream()
			.map(DailyMetricResponse::from)
			.toList());
		return dashboard;
	}

}
