package io.strategiz.social.data.entity;

import java.time.Instant;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import io.cidadel.data.base.annotation.Collection;
import io.cidadel.data.base.entity.BaseEntity;

/**
 * Represents a pending action confirmation for Tier 1 (mutations) and Tier 2 (financial)
 * agent actions. Stored in the action_confirmations Firestore collection.
 */
@IgnoreExtraProperties
@Collection("action_confirmations")
public class ActionConfirmation extends BaseEntity {

	private String id;

	private String userId;

	private String sessionId;

	private String toolName;

	private String actionDescription;

	private String actionPayload;

	private ConfirmationState state;

	private int tier;

	private Instant expiresAt;

	private Instant resolvedAt;

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public String getToolName() {
		return toolName;
	}

	public void setToolName(String toolName) {
		this.toolName = toolName;
	}

	public String getActionDescription() {
		return actionDescription;
	}

	public void setActionDescription(String actionDescription) {
		this.actionDescription = actionDescription;
	}

	public String getActionPayload() {
		return actionPayload;
	}

	public void setActionPayload(String actionPayload) {
		this.actionPayload = actionPayload;
	}

	public ConfirmationState getState() {
		return state;
	}

	public void setState(ConfirmationState state) {
		this.state = state;
	}

	public int getTier() {
		return tier;
	}

	public void setTier(int tier) {
		this.tier = tier;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}

	public void setResolvedAt(Instant resolvedAt) {
		this.resolvedAt = resolvedAt;
	}

	/** Confirmation states for the action approval workflow. */
	public enum ConfirmationState {

		PENDING, APPROVED, DENIED, EXPIRED

	}

}
