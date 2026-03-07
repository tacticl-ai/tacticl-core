package io.tacticl.browser.data.entity;

import java.time.Instant;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

import io.cidadel.data.base.annotation.Collection;
import io.cidadel.data.base.entity.BaseEntity;

/** Represents a cloud browser session managed by Playwright for agent web automation. */
@IgnoreExtraProperties
@Collection("browser_sessions")
public class BrowserSession extends BaseEntity {

	private String id;

	private String userId;

	private String sparkId;

	private BrowserSessionType type;

	private BrowserSessionStatus status;

	private String profilePath;

	private String currentUrl;

	private int pagesOpen;

	private long memoryUsageMb;

	private boolean liveViewEnabled;

	private long durationSeconds;

	private Instant lastActiveAt;

	private Instant closedAt;

	public BrowserSession() {
		this.type = BrowserSessionType.EPHEMERAL;
		this.status = BrowserSessionStatus.ACTIVE;
		this.pagesOpen = 0;
		this.memoryUsageMb = 0;
		this.liveViewEnabled = false;
		this.durationSeconds = 0;
	}

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

	public String getSparkId() {
		return sparkId;
	}

	public void setSparkId(String sparkId) {
		this.sparkId = sparkId;
	}

	public BrowserSessionType getType() {
		return type;
	}

	public void setType(BrowserSessionType type) {
		this.type = type;
	}

	public BrowserSessionStatus getStatus() {
		return status;
	}

	public void setStatus(BrowserSessionStatus status) {
		this.status = status;
	}

	public String getProfilePath() {
		return profilePath;
	}

	public void setProfilePath(String profilePath) {
		this.profilePath = profilePath;
	}

	public String getCurrentUrl() {
		return currentUrl;
	}

	public void setCurrentUrl(String currentUrl) {
		this.currentUrl = currentUrl;
	}

	public int getPagesOpen() {
		return pagesOpen;
	}

	public void setPagesOpen(int pagesOpen) {
		this.pagesOpen = pagesOpen;
	}

	public long getMemoryUsageMb() {
		return memoryUsageMb;
	}

	public void setMemoryUsageMb(long memoryUsageMb) {
		this.memoryUsageMb = memoryUsageMb;
	}

	public boolean isLiveViewEnabled() {
		return liveViewEnabled;
	}

	public void setLiveViewEnabled(boolean liveViewEnabled) {
		this.liveViewEnabled = liveViewEnabled;
	}

	public long getDurationSeconds() {
		return durationSeconds;
	}

	public void setDurationSeconds(long durationSeconds) {
		this.durationSeconds = durationSeconds;
	}

	public Instant getLastActiveAt() {
		return lastActiveAt;
	}

	public void setLastActiveAt(Instant lastActiveAt) {
		this.lastActiveAt = lastActiveAt;
	}

	public Instant getClosedAt() {
		return closedAt;
	}

	public void setClosedAt(Instant closedAt) {
		this.closedAt = closedAt;
	}

}
