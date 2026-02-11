package io.strategiz.social.business.publish;

public class AuthUrl {

	private String url;

	private String codeVerifier;

	public AuthUrl(String url, String codeVerifier) {
		this.url = url;
		this.codeVerifier = codeVerifier;
	}

	public String getUrl() {
		return url;
	}

	public String getCodeVerifier() {
		return codeVerifier;
	}

}
