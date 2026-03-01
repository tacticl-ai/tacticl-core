package io.tacticl.client.gcs.config;

import io.tacticl.client.gcs.client.GcsClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates GCS client beans. */
@Configuration
@ConditionalOnProperty(name = "tacticl.browser.enabled", havingValue = "true")
public class ClientGcsConfig {

	@Bean
	public GcsConfig gcsConfig() {
		return new GcsConfig();
	}

	@Bean
	public GcsClient gcsClient(GcsConfig config) {
		return new GcsClient(config);
	}

}
