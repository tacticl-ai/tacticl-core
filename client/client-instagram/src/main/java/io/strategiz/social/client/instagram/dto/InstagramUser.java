package io.strategiz.social.client.instagram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Instagram user profile returned from the /me endpoint. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstagramUser {

	private String id;

	private String username;

	@JsonProperty("account_type")
	private String accountType;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getAccountType() {
		return accountType;
	}

	public void setAccountType(String accountType) {
		this.accountType = accountType;
	}

}
