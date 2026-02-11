package io.strategiz.social.business.publish;

import java.time.Instant;

public class AuthTokens {

	private String accessToken;

	private String refreshToken;

	private String scope;

	private Instant expiresAt;

	public AuthTokens(String accessToken, String refreshToken, String scope, Instant expiresAt) {
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
		this.scope = scope;
		this.expiresAt = expiresAt;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public String getRefreshToken() {
		return refreshToken;
	}

	public String getScope() {
		return scope;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

}
