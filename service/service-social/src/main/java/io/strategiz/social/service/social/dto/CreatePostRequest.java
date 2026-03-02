package io.strategiz.social.service.social.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Request DTO for creating a social media post. */
public class CreatePostRequest {

	@NotBlank(message = "Content is required")
	private String content;

	private List<String> mediaUrls = new ArrayList<>();

	private List<String> targetIntegrationIds = new ArrayList<>();

	private Instant publishDate;

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

	public Instant getPublishDate() {
		return publishDate;
	}

	public void setPublishDate(Instant publishDate) {
		this.publishDate = publishDate;
	}

}
