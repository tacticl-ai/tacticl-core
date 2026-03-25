package io.strategiz.social.data.entity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import io.cidadel.data.base.annotation.Collection;
import io.cidadel.data.base.entity.BaseEntity;

/**
 * Represents a point where agent execution pauses for user approval. Contains findings and options
 * for user review.
 */
@IgnoreExtraProperties
@Collection("checkpoints")
public class Checkpoint extends BaseEntity {

	private String id;

	private String sparkId;

	private String tacticId;

	private String title;

	private String description;

	private List<Map<String, Object>> findings;

	private List<String> options;

	private CheckpointDecision userDecision;

	private String userFeedback;

	private Instant decidedAt;

	private String pipelineRunId;

	private PdlcRole pdlcRole;

	private CheckpointType checkpointType;

	public Checkpoint() {
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

	public String getTacticId() {
		return tacticId;
	}

	public void setTacticId(String tacticId) {
		this.tacticId = tacticId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public List<Map<String, Object>> getFindings() {
		return findings;
	}

	public void setFindings(List<Map<String, Object>> findings) {
		this.findings = findings;
	}

	public List<String> getOptions() {
		return options;
	}

	public void setOptions(List<String> options) {
		this.options = options;
	}

	public CheckpointDecision getUserDecision() {
		return userDecision;
	}

	public void setUserDecision(CheckpointDecision userDecision) {
		this.userDecision = userDecision;
	}

	public String getUserFeedback() {
		return userFeedback;
	}

	public void setUserFeedback(String userFeedback) {
		this.userFeedback = userFeedback;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public void setDecidedAt(Instant decidedAt) {
		this.decidedAt = decidedAt;
	}

	public String getPipelineRunId() {
		return pipelineRunId;
	}

	public void setPipelineRunId(String pipelineRunId) {
		this.pipelineRunId = pipelineRunId;
	}

	public PdlcRole getPdlcRole() {
		return pdlcRole;
	}

	public void setPdlcRole(PdlcRole pdlcRole) {
		this.pdlcRole = pdlcRole;
	}

	public CheckpointType getCheckpointType() {
		return checkpointType;
	}

	public void setCheckpointType(CheckpointType checkpointType) {
		this.checkpointType = checkpointType;
	}

}
