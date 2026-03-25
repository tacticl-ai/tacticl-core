package io.strategiz.social.service.agent.dto.analytics;

import java.util.List;
import java.util.Map;

/** Combined response DTO for the full analytics dashboard. */
public class DashboardResponse {

	private KeyMetricsResponse keyMetrics;

	private List<RoleAnalyticsResponse> roleAnalytics;

	private ReworkAnalyticsResponse reworkAnalytics;

	private List<FunnelStepResponse> funnel;

	private CostAnalyticsResponse costAnalytics;

	private Map<String, Long> modelDistribution;

	private Map<String, Long> playbookUsage;

	private List<DailyMetricResponse> dailyMetrics;

	public KeyMetricsResponse getKeyMetrics() {
		return keyMetrics;
	}

	public void setKeyMetrics(KeyMetricsResponse keyMetrics) {
		this.keyMetrics = keyMetrics;
	}

	public List<RoleAnalyticsResponse> getRoleAnalytics() {
		return roleAnalytics;
	}

	public void setRoleAnalytics(List<RoleAnalyticsResponse> roleAnalytics) {
		this.roleAnalytics = roleAnalytics;
	}

	public ReworkAnalyticsResponse getReworkAnalytics() {
		return reworkAnalytics;
	}

	public void setReworkAnalytics(ReworkAnalyticsResponse reworkAnalytics) {
		this.reworkAnalytics = reworkAnalytics;
	}

	public List<FunnelStepResponse> getFunnel() {
		return funnel;
	}

	public void setFunnel(List<FunnelStepResponse> funnel) {
		this.funnel = funnel;
	}

	public CostAnalyticsResponse getCostAnalytics() {
		return costAnalytics;
	}

	public void setCostAnalytics(CostAnalyticsResponse costAnalytics) {
		this.costAnalytics = costAnalytics;
	}

	public Map<String, Long> getModelDistribution() {
		return modelDistribution;
	}

	public void setModelDistribution(Map<String, Long> modelDistribution) {
		this.modelDistribution = modelDistribution;
	}

	public Map<String, Long> getPlaybookUsage() {
		return playbookUsage;
	}

	public void setPlaybookUsage(Map<String, Long> playbookUsage) {
		this.playbookUsage = playbookUsage;
	}

	public List<DailyMetricResponse> getDailyMetrics() {
		return dailyMetrics;
	}

	public void setDailyMetrics(List<DailyMetricResponse> dailyMetrics) {
		this.dailyMetrics = dailyMetrics;
	}

}
