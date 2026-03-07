package io.strategiz.social.data.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import io.cidadel.data.base.annotation.Collection;
import io.cidadel.data.base.entity.BaseEntity;

/**
 * Represents a device-local execution unit within a spark. Created by the Device Orchestrator,
 * synced to Cloud for visibility.
 */
@IgnoreExtraProperties
@Collection("tactics")
public class Tactic extends BaseEntity {

	private String id;

	private String sparkId;

	private String deviceId;

	private String description;

	private TacticState status;

	private List<String> repos;

	private Map<String, Object> result;

	private long tokenUsage;

	private Instant completedAt;

	public Tactic() {
		this.status = TacticState.PENDING;
		this.repos = new ArrayList<>();
		this.tokenUsage = 0;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	public String getSparkId() {
		return sparkId;
	}

	public void setSparkId(String sparkId) {
		this.sparkId = sparkId;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public TacticState getStatus() {
		return status;
	}

	public void setStatus(TacticState status) {
		this.status = status;
	}

	public List<String> getRepos() {
		return repos;
	}

	public void setRepos(List<String> repos) {
		this.repos = repos;
	}

	public Map<String, Object> getResult() {
		return result;
	}

	public void setResult(Map<String, Object> result) {
		this.result = result;
	}

	public long getTokenUsage() {
		return tokenUsage;
	}

	public void setTokenUsage(long tokenUsage) {
		this.tokenUsage = tokenUsage;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

}
