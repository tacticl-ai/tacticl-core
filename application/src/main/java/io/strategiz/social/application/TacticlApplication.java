package io.strategiz.social.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
		// Tacticl product code
		"io.strategiz.social",
		"io.tacticl",
		// Cidadel framework
		"io.cidadel.framework.exception",
		"io.cidadel.framework.secrets",
		"io.cidadel.framework.authorization",
		"io.cidadel.framework.logging",
		"io.cidadel.framework.llmrouter",
		"io.cidadel.framework.token",
		"io.cidadel.framework.apidocs",
		// Cidadel service + client base
		"io.cidadel.service.base",
		"io.cidadel.client.base",
		// Cidadel data base (auditing, transactions — excludes Firebase config)
		"io.cidadel.identity.data.base.audit",
		"io.cidadel.identity.data.base.transaction",
		"io.cidadel.identity.data.base.validation",
		// LLM clients
		"io.strategiz.client.anthropic",
		"io.strategiz.client.openai",
		"io.strategiz.client.grok"
})
@EnableScheduling
public class TacticlApplication {

	public static void main(String[] args) {
		SpringApplication.run(TacticlApplication.class, args);
	}

}
