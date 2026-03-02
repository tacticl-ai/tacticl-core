package io.strategiz.social.client.google.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Album {

	@JsonProperty("id")
	private String id;

	@JsonProperty("title")
	private String title;

	@JsonProperty("mediaItemsCount")
	private long mediaItemsCount;

	@JsonProperty("coverPhotoBaseUrl")
	private String coverPhotoBaseUrl;

	@JsonProperty("coverPhotoMediaItemId")
	private String coverPhotoMediaItemId;

	public Album() {
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public long getMediaItemsCount() {
		return mediaItemsCount;
	}

	public void setMediaItemsCount(long mediaItemsCount) {
		this.mediaItemsCount = mediaItemsCount;
	}

	public String getCoverPhotoBaseUrl() {
		return coverPhotoBaseUrl;
	}

	public void setCoverPhotoBaseUrl(String coverPhotoBaseUrl) {
		this.coverPhotoBaseUrl = coverPhotoBaseUrl;
	}

	public String getCoverPhotoMediaItemId() {
		return coverPhotoMediaItemId;
	}

	public void setCoverPhotoMediaItemId(String coverPhotoMediaItemId) {
		this.coverPhotoMediaItemId = coverPhotoMediaItemId;
	}

}
