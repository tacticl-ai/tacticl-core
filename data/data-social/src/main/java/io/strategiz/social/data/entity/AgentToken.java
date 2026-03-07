package io.strategiz.social.data.entity;

import java.util.Map;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import io.cidadel.data.base.annotation.Collection;
import io.cidadel.data.base.entity.BaseEntity;

/** Represents a user-managed API token for agent execution. Token value stored in Vault. */
@IgnoreExtraProperties
@Collection("agent_tokens")
public class AgentToken extends BaseEntity {

	private String id;

	private String userId;

	private TokenProvider provider;

	private String label;

	private String tokenRef;

	private Map<String, Object> usageLimits;

	private Map<String, Object> currentUsage;

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

	public TokenProvider getProvider() {
		return provider;
	}

	public void setProvider(TokenProvider provider) {
		this.provider = provider;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getTokenRef() {
		return tokenRef;
	}

	public void setTokenRef(String tokenRef) {
		this.tokenRef = tokenRef;
	}

	public Map<String, Object> getUsageLimits() {
		return usageLimits;
	}

	public void setUsageLimits(Map<String, Object> usageLimits) {
		this.usageLimits = usageLimits;
	}

	public Map<String, Object> getCurrentUsage() {
		return currentUsage;
	}

	public void setCurrentUsage(Map<String, Object> currentUsage) {
		this.currentUsage = currentUsage;
	}

}
