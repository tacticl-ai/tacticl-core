package io.strategiz.social.service.agent.dto.analytics;

import io.strategiz.social.business.agent.pipeline.AnalyticsService.ReworkAnalytics;
import io.strategiz.social.data.entity.PdlcRole;
import java.util.Map;

/** Response DTO for rework tracking analytics. */
public class ReworkAnalyticsResponse {

	private long totalReworkEvents;

	private double reworkRate;

	private Map<PdlcRole, Integer> reworkByOrigin;

	private double firstPassRate;

	public static ReworkAnalyticsResponse from(ReworkAnalytics analytics) {
		ReworkAnalyticsResponse r = new ReworkAnalyticsResponse();
		r.setTotalReworkEvents(analytics.totalReworkEvents());
		r.setReworkRate(analytics.reworkRate());
		r.setReworkByOrigin(analytics.reworkByOrigin());
		r.setFirstPassRate(analytics.firstPassRate());
		return r;
	}

	public long getTotalReworkEvents() {
		return totalReworkEvents;
	}

	public void setTotalReworkEvents(long totalReworkEvents) {
		this.totalReworkEvents = totalReworkEvents;
	}

	public double getReworkRate() {
		return reworkRate;
	}

	public void setReworkRate(double reworkRate) {
		this.reworkRate = reworkRate;
	}

	public Map<PdlcRole, Integer> getReworkByOrigin() {
		return reworkByOrigin;
	}

	public void setReworkByOrigin(Map<PdlcRole, Integer> reworkByOrigin) {
		this.reworkByOrigin = reworkByOrigin;
	}

	public double getFirstPassRate() {
		return firstPassRate;
	}

	public void setFirstPassRate(double firstPassRate) {
		this.firstPassRate = firstPassRate;
	}

}
