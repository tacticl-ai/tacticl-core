package io.strategiz.social.business.publish;

import io.strategiz.social.data.entity.PlatformType;

/**
 * Interface for social media platform providers. Each platform (Twitter, LinkedIn, Instagram)
 * implements this interface to handle platform-specific publishing and analytics.
 *
 * Follows the same provider pattern used in strategiz-core for brokerage integrations.
 */
public interface SocialMediaProvider {

	PlatformType getPlatformType();

	String getProviderName();

	int getMaxCaptionLength();

	PostValidationResult validate(PostContent content);

	PublishResult publish(PostContent content, String accessToken);

	AuthUrl generateAuthUrl(String redirectUri, String state);

	AuthTokens authenticate(String code, String codeVerifier, String redirectUri);

	AuthTokens refreshToken(String refreshToken);

}
