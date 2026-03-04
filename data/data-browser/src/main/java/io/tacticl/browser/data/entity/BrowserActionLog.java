package io.tacticl.browser.data.entity;

import java.time.Instant;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

import io.cidadel.identity.data.base.annotation.Collection;
import io.cidadel.identity.data.base.entity.BaseEntity;

/** Records a single browser action during agent execution for audit and debugging. */
@IgnoreExtraProperties
@Collection("browser_action_logs")
public class BrowserActionLog extends BaseEntity {

	private String id;

	private String sessionId;

	private String sparkId;

	private String skillName;

	private String url;

	private String elementRef;

	private String inputData;

	private String result;

	private String screenshotUrl;

	private int tier;

	private boolean userApproved;

	private long durationMs;

	private Instant timestamp;

	public BrowserActionLog() {
		this.userApproved = true;
		this.durationMs = 0;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public String getSparkId() {
		return sparkId;
	}

	public void setSparkId(String sparkId) {
		this.sparkId = sparkId;
	}

	public String getSkillName() {
		return skillName;
	}

	public void setSkillName(String skillName) {
		this.skillName = skillName;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getElementRef() {
		return elementRef;
	}

	public void setElementRef(String elementRef) {
		this.elementRef = elementRef;
	}

	public String getInputData() {
		return inputData;
	}

	public void setInputData(String inputData) {
		this.inputData = inputData;
	}

	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}

	public String getScreenshotUrl() {
		return screenshotUrl;
	}

	public void setScreenshotUrl(String screenshotUrl) {
		this.screenshotUrl = screenshotUrl;
	}

	public int getTier() {
		return tier;
	}

	public void setTier(int tier) {
		this.tier = tier;
	}

	public boolean isUserApproved() {
		return userApproved;
	}

	public void setUserApproved(boolean userApproved) {
		this.userApproved = userApproved;
	}

	public long getDurationMs() {
		return durationMs;
	}

	public void setDurationMs(long durationMs) {
		this.durationMs = durationMs;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Instant timestamp) {
		this.timestamp = timestamp;
	}

}
