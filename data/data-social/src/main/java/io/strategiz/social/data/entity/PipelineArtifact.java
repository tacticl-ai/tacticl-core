package io.strategiz.social.data.entity;

import java.time.Instant;
import java.util.Map;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import io.cidadel.data.base.annotation.Collection;
import io.cidadel.data.base.entity.BaseEntity;

/**
 * Represents an artifact produced by a PDLC role during a pipeline run.
 * Examples: requirements doc, architecture design, code changes, test results.
 */
@IgnoreExtraProperties
@Collection("pipeline_artifacts")
public class PipelineArtifact extends BaseEntity {

	private String id;

	private String pipelineRunId;

	private PdlcRole role;

	private String sparkId;

	/**
	 * Type of artifact: REQUIREMENTS, DESIGN, CODE, REVIEW, TEST_RESULTS,
	 * SECURITY_REPORT, DOCS, DEPLOY, PLAN, RESEARCH.
	 */
	private String artifactType;

	/**
	 * Artifact content. For CODE artifacts: {repo, branch, commitSha, filesChanged[], prNumber, prUrl}.
	 */
	private Map<String, Object> content;

	private int artifactVersion;

	private Instant createdAt;

	public PipelineArtifact() {
		this.artifactVersion = 1;
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

	public PdlcRole getRole() {
		return role;
	}

	public void setRole(PdlcRole role) {
		this.role = role;
	}

	public String getSparkId() {
		return sparkId;
	}

	public void setSparkId(String sparkId) {
		this.sparkId = sparkId;
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

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
