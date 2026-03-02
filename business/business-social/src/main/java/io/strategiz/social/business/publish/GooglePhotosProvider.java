package io.strategiz.social.business.publish;

import io.strategiz.social.client.google.config.GoogleConfig;
import io.strategiz.social.data.entity.PlatformType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Google Photos implementation of {@link SocialMediaProvider} — media source only (read-only). */
@Component
@ConditionalOnProperty(name = "tacticl.google.enabled", havingValue = "true", matchIfMissing = false)
public class GooglePhotosProvider implements SocialMediaProvider {

	private static final Logger log = LoggerFactory.getLogger(GooglePhotosProvider.class);

	private static final String GOOGLE_PHOTOS_SCOPE = "https://www.googleapis.com/auth/photoslibrary.readonly";

	private final GoogleConfig googleConfig;

	public GooglePhotosProvider(GoogleConfig googleConfig) {
		this.googleConfig = googleConfig;
	}

	@Override
	public PlatformType getPlatformType() {
		return PlatformType.GOOGLE_PHOTOS;
	}

	@Override
	public String getProviderName() {
		return "Google Photos";
	}

	@Override
	public int getMaxCaptionLength() {
		return PlatformType.GOOGLE_PHOTOS.getMaxCaptionLength();
	}

	@Override
	public PostValidationResult validate(PostContent content) {
		throw new UnsupportedOperationException("Google Photos is a media source — publishing is not supported");
	}

	@Override
	public PublishResult publish(PostContent content, String accessToken) {
		throw new UnsupportedOperationException("Google Photos is a media source — publishing is not supported");
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
				+ "&scope=" + URLEncoder.encode(GOOGLE_PHOTOS_SCOPE, StandardCharsets.UTF_8)
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
