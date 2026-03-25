package io.strategiz.social.service.agent.dto;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineEvent;
import io.strategiz.social.data.entity.PipelineEventType;

import java.time.Instant;
import java.util.Map;

/** Response DTO for a single PDLC pipeline event. */
public class PipelineEventResponse {

	private String id;

	private PipelineEventType eventType;

	private PdlcRole role;

	private int roleIteration;

	private Map<String, Object> metadata;

	private Instant timestamp;

	public static PipelineEventResponse from(PipelineEvent event) {
		PipelineEventResponse response = new PipelineEventResponse();
		response.setId(event.getId());
		response.setEventType(event.getEventType());
		response.setRole(event.getRole());
		response.setRoleIteration(event.getRoleIteration());
		response.setMetadata(event.getMetadata());
		response.setTimestamp(event.getTimestamp());
		return response;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
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
