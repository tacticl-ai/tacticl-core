package io.strategiz.social.data.entity;

import java.math.BigDecimal;

/**
 * Embedded summary of a single PDLC role's execution result within a pipeline run.
 * Stored as values in {@link PipelineRun#getRoleResults()}.
 */
public class RoleResultSummary {

	private String childSparkId;

	private RoleStatus status;

	private String artifactId;

	private int iteration;

	private long tokens;

	private BigDecimal cost;

	private long durationMs;

	private String model;

	private String engine;

	public RoleResultSummary() {
	}

	public String getChildSparkId() {
		return childSparkId;
	}

	public void setChildSparkId(String childSparkId) {
		this.childSparkId = childSparkId;
	}

	public RoleStatus getStatus() {
		return status;
	}

	public void setStatus(RoleStatus status) {
		this.status = status;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public void setArtifactId(String artifactId) {
		this.artifactId = artifactId;
	}

	public int getIteration() {
		return iteration;
	}

	public void setIteration(int iteration) {
		this.iteration = iteration;
	}

	public long getTokens() {
		return tokens;
	}

	public void setTokens(long tokens) {
		this.tokens = tokens;
	}

	public BigDecimal getCost() {
		return cost;
	}

	public void setCost(BigDecimal cost) {
		this.cost = cost;
	}

	public long getDurationMs() {
		return durationMs;
	}

	public void setDurationMs(long durationMs) {
		this.durationMs = durationMs;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getEngine() {
		return engine;
	}

	public void setEngine(String engine) {
		this.engine = engine;
	}

}
