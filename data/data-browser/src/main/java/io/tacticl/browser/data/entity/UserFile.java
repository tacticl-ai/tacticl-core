package io.tacticl.browser.data.entity;

import java.time.Instant;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;

/** Tracks files downloaded or uploaded during browser sessions. */
@IgnoreExtraProperties
public class UserFile {

	private String id;

	private String userId;

	private String sparkId;

	private String sessionId;

	private UserFileType type;

	private String fileName;

	private String contentType;

	private long sizeBytes;

	private String gcsPath;

	private String sourceUrl;

	private Instant createdAt;

	private Instant expiresAt;

	public UserFile() {
		this.sizeBytes = 0;
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

	public String getSparkId() {
		return sparkId;
	}

	public void setSparkId(String sparkId) {
		this.sparkId = sparkId;
	}

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public UserFileType getType() {
		return type;
	}

	public void setType(UserFileType type) {
		this.type = type;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public void setSizeBytes(long sizeBytes) {
		this.sizeBytes = sizeBytes;
	}

	public String getGcsPath() {
		return gcsPath;
	}

	public void setGcsPath(String gcsPath) {
		this.gcsPath = gcsPath;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public void setSourceUrl(String sourceUrl) {
		this.sourceUrl = sourceUrl;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

}
