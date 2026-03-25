package io.strategiz.social.service.agent.dto;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineStatus;
import io.strategiz.social.data.entity.PipelineTier;
import io.strategiz.social.data.entity.PipelineRun;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Response DTO for a PDLC pipeline run. */
public class PipelineRunResponse {

	private String id;

	private String sparkId;

	private String playbook;

	private PipelineTier pipelineTier;

	private PipelineStatus status;

	private List<PdlcRole> activatedRoles;

	private PdlcRole currentRole;

	private int reworkCount;

	private long totalTokens;

	private BigDecimal totalCost;

	private Instant startedAt;

	private Instant completedAt;

	public static PipelineRunResponse from(PipelineRun run) {
		PipelineRunResponse response = new PipelineRunResponse();
		response.setId(run.getId());
		response.setSparkId(run.getSparkId());
		response.setPlaybook(run.getPlaybook());
		response.setPipelineTier(run.getPipelineTier());
		response.setStatus(run.getStatus());
		response.setActivatedRoles(run.getActivatedRoles());
		response.setCurrentRole(run.getCurrentRole());
		response.setReworkCount(run.getReworkCount());
		response.setTotalTokens(run.getTotalTokens());
		response.setTotalCost(run.getTotalCost());
		response.setStartedAt(run.getStartedAt());
		response.setCompletedAt(run.getCompletedAt());
		return response;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSparkId() {
		return sparkId;
	}

	public void setSparkId(String sparkId) {
		this.sparkId = sparkId;
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
