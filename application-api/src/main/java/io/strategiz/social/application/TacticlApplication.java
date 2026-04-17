package io.strategiz.social.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
		"io.strategiz.social.application",
		"io.strategiz.social.service",
		"io.tacticl",
		"io.cidadel.framework",
		"io.cidadel.client.anthropic",
		"io.cidadel.client.openai",
		"io.cidadel.client.grok",
		"io.cidadel.data"
})
@EnableMongoRepositories(basePackages = {"io.tacticl", "io.cidadel.data"})
@EnableScheduling
public class TacticlApplication {

	public static void main(String[] args) {
		SpringApplication.run(TacticlApplication.class, args);
	}

}
