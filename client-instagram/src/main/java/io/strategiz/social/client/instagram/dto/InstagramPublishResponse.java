package io.strategiz.social.client.instagram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Response from Instagram Graph API media publish endpoint. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstagramPublishResponse {

	private String id;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

}
