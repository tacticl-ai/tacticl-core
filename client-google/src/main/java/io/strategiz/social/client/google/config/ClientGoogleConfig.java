package io.strategiz.social.client.google.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the Google OAuth client (YouTube, Gmail).
 *
 * <p>
 * Enable in application.properties:
 * <pre>
 * tacticl.google.enabled=true
 * </pre>
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.google.enabled", havingValue = "true", matchIfMissing = false)
public class ClientGoogleConfig {

	@Bean
	public GoogleConfig googleConfig() {
		return new GoogleConfig();
	}

}
