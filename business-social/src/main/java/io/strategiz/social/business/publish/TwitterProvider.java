package io.strategiz.social.business.publish;

import io.strategiz.social.client.twitter.client.TwitterClient;
import io.strategiz.social.client.twitter.config.TwitterConfig;
import io.strategiz.social.client.twitter.dto.TweetResponse;
import io.strategiz.social.data.entity.PlatformType;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Twitter/X implementation of {@link SocialMediaProvider}. */
@Component
public class TwitterProvider implements SocialMediaProvider {

	private static final Logger log = LoggerFactory.getLogger(TwitterProvider.class);

	private final TwitterClient twitterClient;

	private final TwitterConfig twitterConfig;

	public TwitterProvider(TwitterClient twitterClient, TwitterConfig twitterConfig) {
		this.twitterClient = twitterClient;
		this.twitterConfig = twitterConfig;
	}

	@Override
	public PlatformType getPlatformType() {
		return PlatformType.TWITTER;
	}

	@Override
	public String getProviderName() {
		return "Twitter/X";
	}

	@Override
	public int getMaxCaptionLength() {
		return PlatformType.TWITTER.getMaxCaptionLength();
	}

	@Override
	public PostValidationResult validate(PostContent content) {
		List<String> errors = new ArrayList<>();
		if (content.getText() == null || content.getText().isBlank()) {
			errors.add("Tweet text cannot be empty");
		}
		else if (content.getText().length() > getMaxCaptionLength()) {
			errors.add("Tweet exceeds " + getMaxCaptionLength() + " character limit");
		}
		if (content.getMediaUrls().size() > PlatformType.TWITTER.getMaxImages()) {
			errors.add("Twitter allows a maximum of " + PlatformType.TWITTER.getMaxImages() + " images");
		}
		return errors.isEmpty() ? PostValidationResult.valid() : PostValidationResult.invalid(errors);
	}

	@Override
	public PublishResult publish(PostContent content, String accessToken) {
		log.info("Publishing tweet via TwitterClient");
		try {
			TweetResponse response = twitterClient.postTweet(content.getText(), accessToken);
			String tweetUrl = "https://twitter.com/i/status/" + response.getId();
			return PublishResult.success(response.getId(), tweetUrl);
		}
		catch (Exception e) {
			log.error("Failed to publish tweet: {}", e.getMessage(), e);
			return PublishResult.failed("Twitter publish failed: " + e.getMessage());
		}
	}

	@Override
	public AuthUrl generateAuthUrl(String redirectUri, String state) {
		// Twitter OAuth 2.0 PKCE authorization URL
		String codeVerifier = OAuthPkceUtils.generateCodeVerifier();
		String codeChallenge = OAuthPkceUtils.generateCodeChallenge(codeVerifier);
		String url = "https://twitter.com/i/oauth2/authorize" + "?response_type=code"
				+ "&client_id=" + twitterConfig.getApiKey() + "&redirect_uri=" + redirectUri + "&state=" + state
				+ "&scope=tweet.read+tweet.write+users.read+offline.access" + "&code_challenge=" + codeChallenge
				+ "&code_challenge_method=S256";
		return new AuthUrl(url, codeVerifier);
	}

	@Override
	public AuthTokens authenticate(String code, String codeVerifier, String redirectUri) {
		// Token exchange deferred to OAuth service — requires HTTP POST to Twitter
		throw new UnsupportedOperationException("Token exchange handled by OAuthService");
	}

	@Override
	public AuthTokens refreshToken(String refreshToken) {
		throw new UnsupportedOperationException("Token refresh handled by OAuthService");
	}

}
