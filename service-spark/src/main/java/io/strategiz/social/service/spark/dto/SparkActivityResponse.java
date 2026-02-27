package io.strategiz.social.service.spark.dto;

import java.util.List;

/** Response DTO for the spark activity dashboard. */
public class SparkActivityResponse {

	private List<SparkResponse> activeSparks;

	private List<SparkResponse> recentSparks;

	public SparkActivityResponse() {
	}

	public List<SparkResponse> getActiveSparks() {
		return activeSparks;
	}

	public void setActiveSparks(List<SparkResponse> activeSparks) {
		this.activeSparks = activeSparks;
	}

	public List<SparkResponse> getRecentSparks() {
		return recentSparks;
	}

	public void setRecentSparks(List<SparkResponse> recentSparks) {
		this.recentSparks = recentSparks;
	}

}
