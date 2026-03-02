package io.strategiz.social.business.publish;

import io.strategiz.social.client.linkedin.client.LinkedInClient;
import io.strategiz.social.client.linkedin.config.LinkedInConfig;
import io.strategiz.social.client.linkedin.dto.LinkedInShareResponse;
import io.strategiz.social.data.entity.PlatformType;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** LinkedIn implementation of {@link SocialMediaProvider}. */
@Component
@ConditionalOnProperty(name = "tacticl.linkedin.enabled", havingValue = "true", matchIfMissing = false)
public class LinkedInProvider implements SocialMediaProvider {

	private static final Logger log = LoggerFactory.getLogger(LinkedInProvider.class);

	private final LinkedInClient linkedInClient;

	private final LinkedInConfig linkedInConfig;

	public LinkedInProvider(LinkedInClient linkedInClient, LinkedInConfig linkedInConfig) {
		this.linkedInClient = linkedInClient;
		this.linkedInConfig = linkedInConfig;
	}

	@Override
	public PlatformType getPlatformType() {
		return PlatformType.LINKEDIN;
	}

	@Override
	public String getProviderName() {
		return "LinkedIn";
	}

	@Override
	public int getMaxCaptionLength() {
		return PlatformType.LINKEDIN.getMaxCaptionLength();
	}

	@Override
	public PostValidationResult validate(PostContent content) {
		List<String> errors = new ArrayList<>();
		if (content.getText() == null || content.getText().isBlank()) {
			errors.add("LinkedIn post text cannot be empty");
		}
		else if (content.getText().length() > getMaxCaptionLength()) {
			errors.add("LinkedIn post exceeds " + getMaxCaptionLength() + " character limit");
		}
		return errors.isEmpty() ? PostValidationResult.valid() : PostValidationResult.invalid(errors);
	}

	@Override
	public PublishResult publish(PostContent content, String accessToken) {
		log.info("Publishing share via LinkedInClient");
		try {
			LinkedInShareResponse response = linkedInClient.createShare(content.getText(), accessToken);
			String postUrl = "https://www.linkedin.com/feed/update/" + response.getId();
			return PublishResult.success(response.getId(), postUrl);
		}
		catch (Exception e) {
			log.error("Failed to publish LinkedIn share: {}", e.getMessage(), e);
			return PublishResult.failed("LinkedIn publish failed: " + e.getMessage());
		}
	}

	@Override
	public AuthUrl generateAuthUrl(String redirectUri, String state) {
		String codeVerifier = OAuthPkceUtils.generateCodeVerifier();
		String codeChallenge = OAuthPkceUtils.generateCodeChallenge(codeVerifier);
		String url = "https://www.linkedin.com/oauth/v2/authorization" + "?response_type=code"
				+ "&client_id=" + linkedInConfig.getClientId() + "&redirect_uri=" + redirectUri + "&state=" + state
				+ "&scope=openid+profile+w_member_social" + "&code_challenge=" + codeChallenge
				+ "&code_challenge_method=S256";
		return new AuthUrl(url, codeVerifier);
	}

	@Override
	public AuthTokens authenticate(String code, String codeVerifier, String redirectUri) {
		throw new UnsupportedOperationException("Token exchange handled by OAuthService");
	}

	@Override
	public AuthTokens refreshToken(String refreshToken) {
		throw new UnsupportedOperationException("Token refresh handled by OAuthService");
	}

}
