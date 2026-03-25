package io.strategiz.social.data.entity;

import java.time.Instant;
import java.util.List;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import io.cidadel.data.base.annotation.Collection;
import io.cidadel.data.base.entity.BaseEntity;

/**
 * Represents knowledge accumulated by a PDLC role, used for retrieval-augmented
 * generation during pipeline execution. Populated from retrospectives and manual curation.
 */
@IgnoreExtraProperties
@Collection("pdlc_role_knowledge")
public class PdlcRoleKnowledge extends BaseEntity {

	private String id;

	private PdlcRole role;

	/**
	 * Category of knowledge: BEST_PRACTICE, ANTI_PATTERN, EXAMPLE, RETRO_LEARNING.
	 */
	private String category;

	private String content;

	private List<Double> embedding;

	/**
	 * Origin of this knowledge entry. Examples: "retro-{pipelineRunId}", "manual", "bootstrap".
	 */
	private String source;

	private double relevanceScore;

	private Instant createdAt;

	public PdlcRoleKnowledge() {
		this.relevanceScore = 0.0;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	public PdlcRole getRole() {
		return role;
	}

	public void setRole(PdlcRole role) {
		this.role = role;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public List<Double> getEmbedding() {
		return embedding;
	}

	public void setEmbedding(List<Double> embedding) {
		this.embedding = embedding;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public double getRelevanceScore() {
		return relevanceScore;
	}

	public void setRelevanceScore(double relevanceScore) {
		this.relevanceScore = relevanceScore;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
