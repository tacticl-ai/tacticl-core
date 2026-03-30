package io.strategiz.social.service.agent.dto;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineArtifact;
import io.strategiz.social.data.entity.RoleResultSummary;

import java.time.Instant;
import java.util.Map;

/** Response DTO for a pipeline role artifact, enriched with execution metrics from the role result. */
public class RoleArtifactResponse {

	private String id;

	private PdlcRole role;

	private String artifactType;

	private Map<String, Object> content;

	private int artifactVersion;

	private String model;

	private long tokens;

	private int iteration;

	private Instant createdAt;

	public static RoleArtifactResponse from(PipelineArtifact artifact, RoleResultSummary roleResult) {
		RoleArtifactResponse response = new RoleArtifactResponse();
		response.setId(artifact.getId());
		response.setRole(artifact.getRole());
		response.setArtifactType(artifact.getArtifactType());
		response.setContent(artifact.getContent());
		response.setArtifactVersion(artifact.getArtifactVersion());
		response.setCreatedAt(artifact.getCreatedAt());
		if (roleResult != null) {
			response.setModel(roleResult.getModel());
			response.setTokens(roleResult.getTokens());
			response.setIteration(roleResult.getIteration());
		}
		return response;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public PdlcRole getRole() {
		return role;
	}

	public void setRole(PdlcRole role) {
		this.role = role;
	}

	public String getArtifactType() {
		return artifactType;
	}

	public void setArtifactType(String artifactType) {
		this.artifactType = artifactType;
	}

	public Map<String, Object> getContent() {
		return content;
	}

	public void setContent(Map<String, Object> content) {
		this.content = content;
	}

	public int getArtifactVersion() {
		return artifactVersion;
	}

	public void setArtifactVersion(int artifactVersion) {
		this.artifactVersion = artifactVersion;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public long getTokens() {
		return tokens;
	}

	public void setTokens(long tokens) {
		this.tokens = tokens;
	}

	public int getIteration() {
		return iteration;
	}

	public void setIteration(int iteration) {
		this.iteration = iteration;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
