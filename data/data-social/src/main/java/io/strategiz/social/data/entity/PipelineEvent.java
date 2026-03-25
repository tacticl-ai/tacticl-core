package io.strategiz.social.data.entity;

import java.time.Instant;
import java.util.Map;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import io.cidadel.data.base.annotation.Collection;
import io.cidadel.data.base.entity.BaseEntity;

/**
 * Immutable audit event appended during PDLC pipeline execution.
 * Records every significant state transition and role lifecycle event for observability.
 */
@IgnoreExtraProperties
@Collection("pipeline_events")
public class PipelineEvent extends BaseEntity {

	private String id;

	private String pipelineRunId;

	private String sparkId;

	private String childSparkId;

	private String userId;

	private PipelineEventType eventType;

	private PdlcRole role;

	private int roleIteration;

	private Map<String, Object> metadata;

	private Instant timestamp;

	public PipelineEvent() {
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	public String getPipelineRunId() {
		return pipelineRunId;
	}

	public void setPipelineRunId(String pipelineRunId) {
		this.pipelineRunId = pipelineRunId;
	}

	public String getSparkId() {
		return sparkId;
	}

	public void setSparkId(String sparkId) {
		this.sparkId = sparkId;
	}

	public String getChildSparkId() {
		return childSparkId;
	}

	public void setChildSparkId(String childSparkId) {
		this.childSparkId = childSparkId;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public PipelineEventType getEventType() {
		return eventType;
	}

	public void setEventType(PipelineEventType eventType) {
		this.eventType = eventType;
	}

	public PdlcRole getRole() {
		return role;
	}

	public void setRole(PdlcRole role) {
		this.role = role;
	}

	public int getRoleIteration() {
		return roleIteration;
	}

	public void setRoleIteration(int roleIteration) {
		this.roleIteration = roleIteration;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Instant timestamp) {
		this.timestamp = timestamp;
	}

}
