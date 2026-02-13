package io.strategiz.social.client.twitter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for a created or retrieved tweet from Twitter API v2.
 *
 * <p>
 * Maps to the {@code data} object in the Twitter API v2 response:
 * <pre>
 * { "data": { "id": "1234567890", "text": "Hello world" } }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TweetResponse {

	private String id;

	private String text;

	public TweetResponse() {
	}

	public TweetResponse(String id, String text) {
		this.id = id;
		this.text = text;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

}
