package io.strategiz.social.business.publish;

import io.strategiz.social.client.github.config.GitHubConfig;
import io.strategiz.social.data.entity.PlatformType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** GitHub implementation of {@link SocialMediaProvider} for repo access OAuth. */
@Component
@ConditionalOnProperty(name = "tacticl.github.enabled", havingValue = "true", matchIfMissing = false)
public class GitHubProvider implements SocialMediaProvider {

	private static final Logger log = LoggerFactory.getLogger(GitHubProvider.class);

	private final GitHubConfig gitHubConfig;

	public GitHubProvider(GitHubConfig gitHubConfig) {
		this.gitHubConfig = gitHubConfig;
	}

	@Override
	public PlatformType getPlatformType() {
		return PlatformType.GITHUB;
	}

	@Override
	public String getProviderName() {
		return "GitHub";
	}

	@Override
	public int getMaxCaptionLength() {
		return 0; // Not applicable for GitHub
	}

	@Override
	public PostValidationResult validate(PostContent content) {
		return PostValidationResult.valid();
	}

	@Override
	public PublishResult publish(PostContent content, String accessToken) {
		// GitHub publishing (issues, PRs) handled by device agents via GitHub API
		log.warn("Direct GitHub publish not implemented — use device agent");
		return PublishResult.failed("GitHub operations require device agent execution");
	}

	@Override
	public AuthUrl generateAuthUrl(String redirectUri, String state) {
		// GitHub OAuth 2.0 does not support PKCE
		String url = "https://github.com/login/oauth/authorize"
				+ "?client_id=" + URLEncoder.encode(gitHubConfig.getClientId(), StandardCharsets.UTF_8)
				+ "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
				+ "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
				+ "&scope=" + URLEncoder.encode("repo user read:org", StandardCharsets.UTF_8);
		return new AuthUrl(url, null);
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
