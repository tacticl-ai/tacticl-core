package io.strategiz.social.business.publish;

import io.strategiz.social.client.instagram.client.InstagramClient;
import io.strategiz.social.client.instagram.dto.InstagramMediaResponse;
import io.strategiz.social.client.instagram.dto.InstagramPublishResponse;
import io.strategiz.social.data.entity.PlatformType;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Instagram implementation of {@link SocialMediaProvider}. */
@Component
@ConditionalOnProperty(name = "tacticl.instagram.enabled", havingValue = "true", matchIfMissing = false)
public class InstagramProvider implements SocialMediaProvider {

	private static final Logger log = LoggerFactory.getLogger(InstagramProvider.class);

	private final InstagramClient instagramClient;

	public InstagramProvider(InstagramClient instagramClient) {
		this.instagramClient = instagramClient;
	}

	@Override
	public PlatformType getPlatformType() {
		return PlatformType.INSTAGRAM;
	}

	@Override
	public String getProviderName() {
		return "Instagram";
	}

	@Override
	public int getMaxCaptionLength() {
		return PlatformType.INSTAGRAM.getMaxCaptionLength();
	}

	@Override
	public PostValidationResult validate(PostContent content) {
		List<String> errors = new ArrayList<>();
		if (content.getMediaUrls().isEmpty()) {
			errors.add("Instagram requires at least one image");
		}
		if (content.getText() != null && content.getText().length() > getMaxCaptionLength()) {
			errors.add("Caption exceeds " + getMaxCaptionLength() + " character limit");
		}
		if (content.getMediaUrls().size() > PlatformType.INSTAGRAM.getMaxImages()) {
			errors.add("Instagram allows a maximum of " + PlatformType.INSTAGRAM.getMaxImages() + " images");
		}
		return errors.isEmpty() ? PostValidationResult.valid() : PostValidationResult.invalid(errors);
	}

	@Override
	public PublishResult publish(PostContent content, String accessToken) {
		log.info("Publishing to Instagram via two-step process");
		try {
			// Instagram requires an igUserId — stored in platform metadata.
			// For now the provider expects the first media URL as the image.
			String imageUrl = content.getMediaUrls().get(0);
			String caption = content.getText() != null ? content.getText() : "";

			// Step 1: Create media container (igUserId passed via platformMetadata in
			// future)
			// For MVP, igUserId comes from the integration record, but
			// the caller resolves it before calling publish. We use "me" as placeholder.
			InstagramMediaResponse media = instagramClient.createMedia("me", imageUrl, caption, accessToken);

			// Step 2: Publish
			InstagramPublishResponse published = instagramClient.publishMedia("me", media.getId(), accessToken);

			String postUrl = "https://www.instagram.com/p/" + published.getId();
			return PublishResult.success(published.getId(), postUrl);
		}
		catch (Exception e) {
			log.error("Failed to publish to Instagram: {}", e.getMessage(), e);
			return PublishResult.failed("Instagram publish failed: " + e.getMessage());
		}
	}

	@Override
	public AuthUrl generateAuthUrl(String redirectUri, String state) {
		// Instagram uses Facebook OAuth
		String url = "https://www.facebook.com/v21.0/dialog/oauth" + "?response_type=code"
				+ "&redirect_uri=" + redirectUri + "&state=" + state
				+ "&scope=instagram_basic,instagram_content_publish,pages_show_list";
		return new AuthUrl(url, null);
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
