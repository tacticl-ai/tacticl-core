package io.strategiz.social.data.entity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

/**
 * Represents a point where agent execution pauses for user approval. Contains findings and options
 * for user review.
 */
@IgnoreExtraProperties
public class Checkpoint {

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

	private Instant createdAt;

	public Checkpoint() {
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

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
