package io.strategiz.social.data.entity;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import io.cidadel.data.base.annotation.Collection;
import io.cidadel.data.base.entity.BaseEntity;

/**
 * Stores an admin-configured AI engine override for a specific PDLC role. Document ID equals the
 * role name (e.g. "IMPLEMENTER"). Resolution chain: role override → step override → product
 * defaults.
 */
@IgnoreExtraProperties
@Collection("ai_role_overrides")
public class AiRoleOverride extends BaseEntity {

	private String id;

	/** PDLC role name, e.g. "IMPLEMENTER". Also used as the Firestore document ID. */
	private String role;

	/** Engine identifier, e.g. "anthropic-agentic". */
	private String engineId;

	/** Model identifier, e.g. "claude-opus-4-6". */
	private String model;

	/** Admin user ID who last updated this override. */
	private String updatedBy;

	public AiRoleOverride() {
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public String getEngineId() {
		return engineId;
	}

	public void setEngineId(String engineId) {
		this.engineId = engineId;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(String updatedBy) {
		this.updatedBy = updatedBy;
	}

}
