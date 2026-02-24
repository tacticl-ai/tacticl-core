package io.strategiz.social.business.publish;

import io.strategiz.social.client.google.config.GoogleConfig;
import io.strategiz.social.data.entity.PlatformType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** YouTube implementation of {@link SocialMediaProvider} using Google OAuth 2.0. */
@Component
@ConditionalOnProperty(name = "tacticl.google.enabled", havingValue = "true", matchIfMissing = false)
public class YouTubeProvider implements SocialMediaProvider {

	private static final Logger log = LoggerFactory.getLogger(YouTubeProvider.class);

	private static final String YOUTUBE_SCOPES = "https://www.googleapis.com/auth/youtube"
			+ " https://www.googleapis.com/auth/youtube.upload"
			+ " https://www.googleapis.com/auth/youtube.readonly";

	private final GoogleConfig googleConfig;

	public YouTubeProvider(GoogleConfig googleConfig) {
		this.googleConfig = googleConfig;
	}

	@Override
	public PlatformType getPlatformType() {
		return PlatformType.YOUTUBE;
	}

	@Override
	public String getProviderName() {
		return "YouTube";
	}

	@Override
	public int getMaxCaptionLength() {
		return PlatformType.YOUTUBE.getMaxCaptionLength();
	}

	@Override
	public PostValidationResult validate(PostContent content) {
		List<String> errors = new ArrayList<>();
		if (content.getText() == null || content.getText().isBlank()) {
			errors.add("Video title/description cannot be empty");
		}
		else if (content.getText().length() > getMaxCaptionLength()) {
			errors.add("Description exceeds " + getMaxCaptionLength() + " character limit");
		}
		return errors.isEmpty() ? PostValidationResult.valid() : PostValidationResult.invalid(errors);
	}

	@Override
	public PublishResult publish(PostContent content, String accessToken) {
		// YouTube publish requires video upload via YouTube Data API v3 — complex flow
		// handled by device agents via browser automation or direct API calls
		log.warn("Direct YouTube publish not implemented — use device agent for video uploads");
		return PublishResult.failed("YouTube video uploads require device agent execution");
	}

	@Override
	public AuthUrl generateAuthUrl(String redirectUri, String state) {
		String codeVerifier = OAuthPkceUtils.generateCodeVerifier();
		String codeChallenge = OAuthPkceUtils.generateCodeChallenge(codeVerifier);
		String url = "https://accounts.google.com/o/oauth2/v2/auth"
				+ "?response_type=code"
				+ "&client_id=" + URLEncoder.encode(googleConfig.getClientId(), StandardCharsets.UTF_8)
				+ "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
				+ "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
				+ "&scope=" + URLEncoder.encode(YOUTUBE_SCOPES, StandardCharsets.UTF_8)
				+ "&access_type=offline"
				+ "&prompt=consent"
				+ "&code_challenge=" + URLEncoder.encode(codeChallenge, StandardCharsets.UTF_8)
				+ "&code_challenge_method=S256";
		return new AuthUrl(url, codeVerifier);
	}

	@Override
	public AuthTokens authenticate(String code, String codeVerifier, String redirectUri) {
		throw new UnsupportedOperationException("Token exchange handled by OAuthTokenExchangeService");
	}

	@Override
	public AuthTokens refreshToken(String refreshToken) {
		throw new UnsupportedOperationException("Token refresh handled by OAuthTokenExchangeService");
	}

}
