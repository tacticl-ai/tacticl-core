package io.strategiz.social.data.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import io.cidadel.data.base.annotation.Collection;
import io.cidadel.data.base.entity.BaseEntity;

/**
 * Represents a single execution run of the PDLC pipeline for a given spark.
 * Top-level collection document containing role execution state and cost tracking.
 */
@IgnoreExtraProperties
@Collection("pipeline_runs")
public class PipelineRun extends BaseEntity {

	private String id;

	private String sparkId;

	private String userId;

	private String playbook;

	private PipelineTier pipelineTier;

	private PipelineStatus status;

	private List<PdlcRole> activatedRoles;

	private PdlcRole currentRole;

	private Map<String, RoleResultSummary> roleResults;

	private int reworkCount;

	private long totalTokens;

	private BigDecimal totalCost;

	private Map<String, Object> classificationResult;

	private Map<String, Object> gitContext;

	/**
	 * Role names that the user explicitly skipped but are marked {@code required} in the playbook.
	 * Non-empty triggers a soft-guardrail confirmation checkpoint at pipeline start.
	 */
	private List<String> skippedRequiredRoles;

	private String claimedBy;

	private Instant claimedAt;

	private Instant startedAt;

	private Instant completedAt;

	public PipelineRun() {
		this.status = PipelineStatus.CREATED;
		this.activatedRoles = new ArrayList<>();
		this.roleResults = new ConcurrentHashMap<>();
		this.reworkCount = 0;
		this.totalTokens = 0;
		this.totalCost = BigDecimal.ZERO;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	public String getSparkId() {
		return sparkId;
	}

	public void setSparkId(String sparkId) {
		this.sparkId = sparkId;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getPlaybook() {
		return playbook;
	}

	public void setPlaybook(String playbook) {
		this.playbook = playbook;
	}

	public PipelineTier getPipelineTier() {
		return pipelineTier;
	}

	public void setPipelineTier(PipelineTier pipelineTier) {
		this.pipelineTier = pipelineTier;
	}

	public PipelineStatus getStatus() {
		return status;
	}

	public void setStatus(PipelineStatus status) {
		this.status = status;
	}

	public List<PdlcRole> getActivatedRoles() {
		return activatedRoles;
	}

	public void setActivatedRoles(List<PdlcRole> activatedRoles) {
		this.activatedRoles = activatedRoles;
	}

	public PdlcRole getCurrentRole() {
		return currentRole;
	}

	public void setCurrentRole(PdlcRole currentRole) {
		this.currentRole = currentRole;
	}

	public Map<String, RoleResultSummary> getRoleResults() {
		return roleResults;
	}

	public void setRoleResults(Map<String, RoleResultSummary> roleResults) {
		this.roleResults = roleResults;
	}

	public int getReworkCount() {
		return reworkCount;
	}

	public void setReworkCount(int reworkCount) {
		this.reworkCount = reworkCount;
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

	public Map<String, Object> getClassificationResult() {
		return classificationResult;
	}

	public void setClassificationResult(Map<String, Object> classificationResult) {
		this.classificationResult = classificationResult;
	}

	public Map<String, Object> getGitContext() {
		return gitContext;
	}

	public void setGitContext(Map<String, Object> gitContext) {
		this.gitContext = gitContext;
	}

	public List<String> getSkippedRequiredRoles() {
		return skippedRequiredRoles;
	}

	public void setSkippedRequiredRoles(List<String> skippedRequiredRoles) {
		this.skippedRequiredRoles = skippedRequiredRoles;
	}

	public String getClaimedBy() {
		return claimedBy;
	}

	public void setClaimedBy(String claimedBy) {
		this.claimedBy = claimedBy;
	}

	public Instant getClaimedAt() {
		return claimedAt;
	}

	public void setClaimedAt(Instant claimedAt) {
		this.claimedAt = claimedAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

}
