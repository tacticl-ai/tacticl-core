package io.strategiz.social.service.agent.dto.analytics;

import io.strategiz.social.business.agent.pipeline.AnalyticsService.KeyMetrics;
import java.math.BigDecimal;

/** Response DTO for top-level dashboard key metrics. */
public class KeyMetricsResponse {

	private long totalPipelineRuns;

	private double successRate;

	private long activePipelines;

	private long pipelineBacklog;

	private long totalTokens;

	private BigDecimal estimatedCost;

	public static KeyMetricsResponse from(KeyMetrics metrics) {
		KeyMetricsResponse r = new KeyMetricsResponse();
		r.setTotalPipelineRuns(metrics.totalPipelineRuns());
		r.setSuccessRate(metrics.successRate());
		r.setActivePipelines(metrics.activePipelines());
		r.setPipelineBacklog(metrics.pipelineBacklog());
		r.setTotalTokens(metrics.totalTokens());
		r.setEstimatedCost(metrics.estimatedCost());
		return r;
	}

	public long getTotalPipelineRuns() {
		return totalPipelineRuns;
	}

	public void setTotalPipelineRuns(long totalPipelineRuns) {
		this.totalPipelineRuns = totalPipelineRuns;
	}

	public double getSuccessRate() {
		return successRate;
	}

	public void setSuccessRate(double successRate) {
		this.successRate = successRate;
	}

	public long getActivePipelines() {
		return activePipelines;
	}

	public void setActivePipelines(long activePipelines) {
		this.activePipelines = activePipelines;
	}

	public long getPipelineBacklog() {
		return pipelineBacklog;
	}

	public void setPipelineBacklog(long pipelineBacklog) {
		this.pipelineBacklog = pipelineBacklog;
	}

	public long getTotalTokens() {
		return totalTokens;
	}

	public void setTotalTokens(long totalTokens) {
		this.totalTokens = totalTokens;
	}

	public BigDecimal getEstimatedCost() {
		return estimatedCost;
	}

	public void setEstimatedCost(BigDecimal estimatedCost) {
		this.estimatedCost = estimatedCost;
	}

}
