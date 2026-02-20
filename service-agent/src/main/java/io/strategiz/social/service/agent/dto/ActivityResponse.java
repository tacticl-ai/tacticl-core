package io.strategiz.social.service.agent.dto;

import java.util.List;
import java.util.Map;

/** Response DTO for the activity dashboard. */
public class ActivityResponse {

	private List<Map<String, Object>> activeAsks;

	private List<Map<String, Object>> recentAsks;

	public ActivityResponse() {
	}

	public List<Map<String, Object>> getActiveAsks() {
		return activeAsks;
	}

	public void setActiveAsks(List<Map<String, Object>> activeAsks) {
		this.activeAsks = activeAsks;
	}

	public List<Map<String, Object>> getRecentAsks() {
		return recentAsks;
	}

	public void setRecentAsks(List<Map<String, Object>> recentAsks) {
		this.recentAsks = recentAsks;
	}

}
