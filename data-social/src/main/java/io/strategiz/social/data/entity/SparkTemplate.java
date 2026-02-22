package io.strategiz.social.data.entity;

import java.util.ArrayList;
import java.util.List;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

/** Reusable template for creating sparks with pre-configured defaults. */
@IgnoreExtraProperties
public class SparkTemplate {

	private String id;

	private String userId;

	private String name;

	private String description;

	private List<String> defaultRepos;

	private String defaultSchedule;

	private CheckpointPolicy defaultCheckpointPolicy;

	private List<String> tags;

	private boolean isActive;

	public SparkTemplate() {
		this.defaultRepos = new ArrayList<>();
		this.tags = new ArrayList<>();
		this.defaultCheckpointPolicy = CheckpointPolicy.CHECKPOINT_MAJOR;
		this.isActive = true;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public List<String> getDefaultRepos() {
		return defaultRepos;
	}

	public void setDefaultRepos(List<String> defaultRepos) {
		this.defaultRepos = defaultRepos;
	}

	public String getDefaultSchedule() {
		return defaultSchedule;
	}

	public void setDefaultSchedule(String defaultSchedule) {
		this.defaultSchedule = defaultSchedule;
	}

	public CheckpointPolicy getDefaultCheckpointPolicy() {
		return defaultCheckpointPolicy;
	}

	public void setDefaultCheckpointPolicy(CheckpointPolicy defaultCheckpointPolicy) {
		this.defaultCheckpointPolicy = defaultCheckpointPolicy;
	}

	public List<String> getTags() {
		return tags;
	}

	public void setTags(List<String> tags) {
		this.tags = tags;
	}

	public boolean isActive() {
		return isActive;
	}

	public void setActive(boolean active) {
		isActive = active;
	}

}
