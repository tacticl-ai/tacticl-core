package io.strategiz.social.service.agent.dto.analytics;

import io.strategiz.social.business.agent.pipeline.AnalyticsService.CostAnalytics;
import io.strategiz.social.data.entity.PdlcRole;
import java.math.BigDecimal;
import java.util.Map;

/** Response DTO for cost analytics broken down by role, playbook, and averages. */
public class CostAnalyticsResponse {

	private Map<PdlcRole, BigDecimal> costByRole;

	private Map<String, BigDecimal> costByPlaybook;

	private BigDecimal avgCostPerPipeline;

	private double costCeilingHitRate;

	public static CostAnalyticsResponse from(CostAnalytics analytics) {
		CostAnalyticsResponse r = new CostAnalyticsResponse();
		r.setCostByRole(analytics.costByRole());
		r.setCostByPlaybook(analytics.costByPlaybook());
		r.setAvgCostPerPipeline(analytics.avgCostPerPipeline());
		r.setCostCeilingHitRate(analytics.costCeilingHitRate());
		return r;
	}

	public Map<PdlcRole, BigDecimal> getCostByRole() {
		return costByRole;
	}

	public void setCostByRole(Map<PdlcRole, BigDecimal> costByRole) {
		this.costByRole = costByRole;
	}

	public Map<String, BigDecimal> getCostByPlaybook() {
		return costByPlaybook;
	}

	public void setCostByPlaybook(Map<String, BigDecimal> costByPlaybook) {
		this.costByPlaybook = costByPlaybook;
	}

	public BigDecimal getAvgCostPerPipeline() {
		return avgCostPerPipeline;
	}

	public void setAvgCostPerPipeline(BigDecimal avgCostPerPipeline) {
		this.avgCostPerPipeline = avgCostPerPipeline;
	}

	public double getCostCeilingHitRate() {
		return costCeilingHitRate;
	}

	public void setCostCeilingHitRate(double costCeilingHitRate) {
		this.costCeilingHitRate = costCeilingHitRate;
	}

}
