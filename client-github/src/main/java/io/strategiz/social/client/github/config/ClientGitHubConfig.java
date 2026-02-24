package io.strategiz.social.client.github.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the GitHub OAuth client.
 *
 * <p>
 * Enable in application.properties:
 * <pre>
 * tacticl.github.enabled=true
 * </pre>
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.github.enabled", havingValue = "true", matchIfMissing = false)
public class ClientGitHubConfig {

	@Bean
	public GitHubConfig gitHubConfig() {
		return new GitHubConfig();
	}

}
