package io.strategiz.social.service.agent.dto.analytics;

import io.strategiz.social.business.agent.pipeline.AnalyticsService.DailyMetric;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Response DTO for daily aggregated metrics. */
public class DailyMetricResponse {

	private LocalDate date;

	private long pipelineRuns;

	private double successRate;

	private long totalTokens;

	private BigDecimal totalCost;

	public static DailyMetricResponse from(DailyMetric metric) {
		DailyMetricResponse r = new DailyMetricResponse();
		r.setDate(metric.date());
		r.setPipelineRuns(metric.pipelineRuns());
		r.setSuccessRate(metric.successRate());
		r.setTotalTokens(metric.totalTokens());
		r.setTotalCost(metric.totalCost());
		return r;
	}

	public LocalDate getDate() {
		return date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public long getPipelineRuns() {
		return pipelineRuns;
	}

	public void setPipelineRuns(long pipelineRuns) {
		this.pipelineRuns = pipelineRuns;
	}

	public double getSuccessRate() {
		return successRate;
	}

	public void setSuccessRate(double successRate) {
		this.successRate = successRate;
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

}
