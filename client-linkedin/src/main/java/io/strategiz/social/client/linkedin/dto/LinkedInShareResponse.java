package io.strategiz.social.client.linkedin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response from LinkedIn UGC Post creation.
 *
 * <p>
 * The {@code id} field contains the URN of the created post (e.g.,
 * "urn:li:share:1234567890"). The {@code activity} field contains the activity URN that
 * can be used to construct the post URL.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LinkedInShareResponse {

	private String id;

	private String activity;

	public LinkedInShareResponse() {
	}

	public LinkedInShareResponse(String id, String activity) {
		this.id = id;
		this.activity = activity;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getActivity() {
		return activity;
	}

	public void setActivity(String activity) {
		this.activity = activity;
	}

}
