package io.strategiz.social.data.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import io.cidadel.data.base.annotation.Collection;
import io.cidadel.data.base.entity.BaseEntity;

/**
 * Represents a social media post that can be scheduled and published to one or more platforms.
 */
@IgnoreExtraProperties
@Collection("social_posts")
public class SocialPost extends BaseEntity {

	private String id;

	private String userId;

	private String workspaceId;

	private String content;

	private Map<String, String> platformSpecificContent = new HashMap<>();

	private List<String> mediaUrls = new ArrayList<>();

	private List<String> targetIntegrationIds = new ArrayList<>();

	private Instant publishDate;

	private PostState state = PostState.DRAFT;

	private Instant stateChangedAt;

	private String publishedPostId;

	private String publishedUrl;

	private int retryCount;

	private String lastError;

	// Getters and setters

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

	public String getWorkspaceId() {
		return workspaceId;
	}

	public void setWorkspaceId(String workspaceId) {
		this.workspaceId = workspaceId;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public Map<String, String> getPlatformSpecificContent() {
		return platformSpecificContent;
	}

	public void setPlatformSpecificContent(Map<String, String> platformSpecificContent) {
		this.platformSpecificContent = platformSpecificContent;
	}

	public List<String> getMediaUrls() {
		return mediaUrls;
	}

	public void setMediaUrls(List<String> mediaUrls) {
		this.mediaUrls = mediaUrls;
	}

	public List<String> getTargetIntegrationIds() {
		return targetIntegrationIds;
	}

	public void setTargetIntegrationIds(List<String> targetIntegrationIds) {
		this.targetIntegrationIds = targetIntegrationIds;
	}

	public Instant getPublishDate() {
		return publishDate;
	}

	public void setPublishDate(Instant publishDate) {
		this.publishDate = publishDate;
	}

	public PostState getState() {
		return state;
	}

	public void setState(PostState state) {
		this.state = state;
		this.stateChangedAt = Instant.now();
	}

	public Instant getStateChangedAt() {
		return stateChangedAt;
	}

	public void setStateChangedAt(Instant stateChangedAt) {
		this.stateChangedAt = stateChangedAt;
	}

	public String getPublishedPostId() {
		return publishedPostId;
	}

	public void setPublishedPostId(String publishedPostId) {
		this.publishedPostId = publishedPostId;
	}

	public String getPublishedUrl() {
		return publishedUrl;
	}

	public void setPublishedUrl(String publishedUrl) {
		this.publishedUrl = publishedUrl;
	}

	public int getRetryCount() {
		return retryCount;
	}

	public void setRetryCount(int retryCount) {
		this.retryCount = retryCount;
	}

	public String getLastError() {
		return lastError;
	}

	public void setLastError(String lastError) {
		this.lastError = lastError;
	}

}
