package io.strategiz.social.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = { "io.strategiz.social", "io.strategiz.framework.exception",
		"io.strategiz.framework.secrets", "io.strategiz.framework.authorization",
		"io.strategiz.framework.logging", "io.strategiz.framework.llmrouter",
		"io.strategiz.client.anthropic", "io.strategiz.client.openai", "io.strategiz.client.grok",
		"io.strategiz.framework.token" })
@EnableScheduling
public class TacticlApplication {

	public static void main(String[] args) {
		SpringApplication.run(TacticlApplication.class, args);
	}

}
