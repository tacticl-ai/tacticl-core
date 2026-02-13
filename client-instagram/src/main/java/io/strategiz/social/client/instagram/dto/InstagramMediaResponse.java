package io.strategiz.social.client.instagram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Response from Instagram Graph API media container creation endpoint. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstagramMediaResponse {

	private String id;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

}
