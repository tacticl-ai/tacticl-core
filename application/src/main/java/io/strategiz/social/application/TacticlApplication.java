package io.strategiz.social.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = { "io.strategiz.social", "io.strategiz.framework.exception",
		"io.strategiz.framework.secrets", "io.strategiz.framework.authorization",
		"io.strategiz.framework.logging", "io.strategiz.framework.llmrouter",
		"io.strategiz.client.anthropic", "io.strategiz.client.openai", "io.strategiz.client.grok",
		"io.strategiz.framework.token", "io.strategiz.framework.firebase", "io.strategiz.framework.apidocs",
		"io.strategiz.service.auth", "io.strategiz.business.tokenauth", "io.strategiz.data.auth",
		"io.strategiz.data.user", "io.strategiz.data.device", "io.strategiz.data.session",
		"io.strategiz.data.framework", "io.strategiz.data.base", "io.strategiz.data.preferences",
		"io.strategiz.data.featureflags", "io.strategiz.data.watchlist",
		"io.strategiz.client.sendgrid", "io.strategiz.client.recaptcha", "io.strategiz.client.vault",
		"io.strategiz.client.google", "io.strategiz.client.facebook", "io.strategiz.client.firebasesms",
		"io.strategiz.client.webpush", "io.strategiz.business.risk" })
@EnableScheduling
public class TacticlApplication {

	public static void main(String[] args) {
		SpringApplication.run(TacticlApplication.class, args);
	}

}
