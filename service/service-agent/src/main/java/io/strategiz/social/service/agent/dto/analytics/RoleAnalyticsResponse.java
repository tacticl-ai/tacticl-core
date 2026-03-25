package io.strategiz.social.service.agent.dto.analytics;

import io.strategiz.social.business.agent.pipeline.AnalyticsService.RoleAnalytics;
import io.strategiz.social.data.entity.PdlcRole;
import java.math.BigDecimal;

/** Response DTO for per-role proficiency analytics. */
public class RoleAnalyticsResponse {

	private PdlcRole role;

	private int runCount;

	private long avgDurationMs;

	private long totalTokens;

	private BigDecimal totalCost;

	private double firstPassRate;

	private double avgReworkIterations;

	public static RoleAnalyticsResponse from(RoleAnalytics analytics) {
		RoleAnalyticsResponse r = new RoleAnalyticsResponse();
		r.setRole(analytics.role());
		r.setRunCount(analytics.runCount());
		r.setAvgDurationMs(analytics.avgDurationMs());
		r.setTotalTokens(analytics.totalTokens());
		r.setTotalCost(analytics.totalCost());
		r.setFirstPassRate(analytics.firstPassRate());
		r.setAvgReworkIterations(analytics.avgReworkIterations());
		return r;
	}

	public PdlcRole getRole() {
		return role;
	}

	public void setRole(PdlcRole role) {
		this.role = role;
	}

	public int getRunCount() {
		return runCount;
	}

	public void setRunCount(int runCount) {
		this.runCount = runCount;
	}

	public long getAvgDurationMs() {
		return avgDurationMs;
	}

	public void setAvgDurationMs(long avgDurationMs) {
		this.avgDurationMs = avgDurationMs;
	}

	public long getTotalTokens() {
		return totalTokens;
	}

	public void setTotalTokens(long totalTokens) {
		this.totalTokens = totalTokens;
	}

	public BigDecimal getTotalCost() {
		return totalCost;
	}

	public void setTotalCost(BigDecimal totalCost) {
		this.totalCost = totalCost;
	}

	public double getFirstPassRate() {
		return firstPassRate;
	}

	public void setFirstPassRate(double firstPassRate) {
		this.firstPassRate = firstPassRate;
	}

	public double getAvgReworkIterations() {
		return avgReworkIterations;
	}

	public void setAvgReworkIterations(double avgReworkIterations) {
		this.avgReworkIterations = avgReworkIterations;
	}

}
