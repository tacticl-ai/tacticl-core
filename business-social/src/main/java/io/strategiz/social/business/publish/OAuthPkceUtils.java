package io.strategiz.social.business.publish;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Utility for OAuth 2.0 PKCE (RFC 7636) code verifier and challenge generation. */
public final class OAuthPkceUtils {

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final int VERIFIER_LENGTH = 64;

	private OAuthPkceUtils() {
	}

	/** Generate a cryptographically random code verifier (43-128 characters). */
	public static String generateCodeVerifier() {
		byte[] bytes = new byte[VERIFIER_LENGTH];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** Generate S256 code challenge from verifier. */
	public static String generateCodeChallenge(String codeVerifier) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
		}
		catch (Exception e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}

}
