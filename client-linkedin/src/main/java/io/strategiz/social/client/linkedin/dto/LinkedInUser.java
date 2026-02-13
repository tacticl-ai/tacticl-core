package io.strategiz.social.client.linkedin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * LinkedIn user profile information from the /v2/userinfo endpoint (OpenID Connect).
 *
 * <p>
 * The {@code sub} field is the unique member identifier used as the author URN when
 * creating posts (e.g., "urn:li:person:{sub}").
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LinkedInUser {

	private String sub;

	private String name;

	private String email;

	private String picture;

	public LinkedInUser() {
	}

	public LinkedInUser(String sub, String name, String email, String picture) {
		this.sub = sub;
		this.name = name;
		this.email = email;
		this.picture = picture;
	}

	public String getSub() {
		return sub;
	}

	public void setSub(String sub) {
		this.sub = sub;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPicture() {
		return picture;
	}

	public void setPicture(String picture) {
		this.picture = picture;
	}

}
