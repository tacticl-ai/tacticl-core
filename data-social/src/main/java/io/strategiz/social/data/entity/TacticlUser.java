package io.strategiz.social.data.entity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Tacticl user record stored in the tacticl_users Firestore collection. */
public class TacticlUser {

	private String id;

	private Instant createdAt;

	private Map<String, Object> preferences = new HashMap<>();

	private boolean onboardingComplete;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Map<String, Object> getPreferences() {
		return preferences;
	}

	public void setPreferences(Map<String, Object> preferences) {
		this.preferences = preferences;
	}

	public boolean isOnboardingComplete() {
		return onboardingComplete;
	}

	public void setOnboardingComplete(boolean onboardingComplete) {
		this.onboardingComplete = onboardingComplete;
	}

}
