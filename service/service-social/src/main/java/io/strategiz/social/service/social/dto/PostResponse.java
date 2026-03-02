package io.strategiz.social.service.social.dto;

import java.time.Instant;
import java.util.List;

/** Response DTO for a social media post. */
public class PostResponse {

	private String id;

	private String content;

	private List<String> mediaUrls;

	private List<String> targetIntegrationIds;

	private String state;

	private Instant publishDate;

	private String publishedPostId;

	private String publishedUrl;

	private Instant createdAt;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
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

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public Instant getPublishDate() {
		return publishDate;
	}

	public void setPublishDate(Instant publishDate) {
		this.publishDate = publishDate;
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

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
