package io.strategiz.social.service.social.dto;

/** Response DTO for the OAuth authorize endpoint, containing the auth URL and PKCE code verifier. */
public class OAuthAuthorizeResponse {

	private String authUrl;

	private String codeVerifier;

	public OAuthAuthorizeResponse(String authUrl, String codeVerifier) {
		this.authUrl = authUrl;
		this.codeVerifier = codeVerifier;
	}

	public String getAuthUrl() {
		return authUrl;
	}

	public void setAuthUrl(String authUrl) {
		this.authUrl = authUrl;
	}

	public String getCodeVerifier() {
		return codeVerifier;
	}

	public void setCodeVerifier(String codeVerifier) {
		this.codeVerifier = codeVerifier;
	}

}
