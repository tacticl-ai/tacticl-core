package io.strategiz.social.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
		"io.tacticl",
		"io.cidadel.framework",
		"io.cidadel.client.anthropic",
		"io.cidadel.client.openai",
		"io.cidadel.client.grok",
		"io.cidadel.framework.ai.engine",
		"io.cidadel.business.ai.engine"
})
@EnableScheduling
public class TacticlApplication {

	public static void main(String[] args) {
		SpringApplication.run(TacticlApplication.class, args);
	}

}
