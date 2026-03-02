package io.strategiz.social.client.google.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaItem {

	@JsonProperty("id")
	private String id;

	@JsonProperty("baseUrl")
	private String baseUrl;

	@JsonProperty("mimeType")
	private String mimeType;

	@JsonProperty("filename")
	private String filename;

	@JsonProperty("mediaMetadata")
	private MediaMetadata mediaMetadata;

	@JsonProperty("productUrl")
	private String productUrl;

	public MediaItem() {
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getMimeType() {
		return mimeType;
	}

	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public MediaMetadata getMediaMetadata() {
		return mediaMetadata;
	}

	public void setMediaMetadata(MediaMetadata mediaMetadata) {
		this.mediaMetadata = mediaMetadata;
	}

	public String getProductUrl() {
		return productUrl;
	}

	public void setProductUrl(String productUrl) {
		this.productUrl = productUrl;
	}

	/** Get sized download URL. Append =w{width}-h{height} to baseUrl. */
	public String getSizedUrl(int width, int height) {
		return baseUrl + "=w" + width + "-h" + height;
	}

	/** Get full-resolution download URL. */
	public String getFullResolutionUrl() {
		return baseUrl + "=d";
	}

}
