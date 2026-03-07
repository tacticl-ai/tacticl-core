package io.strategiz.social.data.entity;

import java.util.HashMap;
import java.util.Map;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import io.cidadel.data.base.annotation.Collection;
import io.cidadel.data.base.entity.BaseEntity;

/** Tacticl user record stored in the tacticl_users Firestore collection. */
@IgnoreExtraProperties
@Collection("tacticl_users")
public class TacticlUser extends BaseEntity {

	private String id;

	private Map<String, Object> preferences = new HashMap<>();

	private boolean onboardingComplete;

	private UserConfig config;

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
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

	public UserConfig getConfig() {
		return config;
	}

	public void setConfig(UserConfig config) {
		this.config = config;
	}

}
