package io.strategiz.social.service.agent.dto.analytics;

import io.strategiz.social.business.agent.pipeline.AnalyticsService.FunnelStep;
import io.strategiz.social.data.entity.PdlcRole;

/** Response DTO for a single step in the pipeline funnel. */
public class FunnelStepResponse {

	private PdlcRole role;

	private long count;

	private double dropOffPercentage;

	public static FunnelStepResponse from(FunnelStep step) {
		FunnelStepResponse r = new FunnelStepResponse();
		r.setRole(step.role());
		r.setCount(step.count());
		r.setDropOffPercentage(step.dropOffPercentage());
		return r;
	}

	public PdlcRole getRole() {
		return role;
	}

	public void setRole(PdlcRole role) {
		this.role = role;
	}

	public long getCount() {
		return count;
	}

	public void setCount(long count) {
		this.count = count;
	}

	public double getDropOffPercentage() {
		return dropOffPercentage;
	}

	public void setDropOffPercentage(double dropOffPercentage) {
		this.dropOffPercentage = dropOffPercentage;
	}

}
