package io.strategiz.social.client.twitter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for a Twitter user profile from the API v2 /2/users/me endpoint.
 *
 * <p>
 * Maps to the {@code data} object in the Twitter API v2 response:
 * <pre>
 * { "data": { "id": "123", "name": "Display Name", "username": "handle", "profile_image_url": "..." } }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TwitterUser {

	private String id;

	private String name;

	private String username;

	@JsonProperty("profile_image_url")
	private String profileImageUrl;

	public TwitterUser() {
	}

	public TwitterUser(String id, String name, String username, String profileImageUrl) {
		this.id = id;
		this.name = name;
		this.username = username;
		this.profileImageUrl = profileImageUrl;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getProfileImageUrl() {
		return profileImageUrl;
	}

	public void setProfileImageUrl(String profileImageUrl) {
		this.profileImageUrl = profileImageUrl;
	}

}
