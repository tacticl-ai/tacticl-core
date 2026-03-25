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
		"io.cidadel.data.base.audit",
		"io.cidadel.data.base.transaction",
		"io.cidadel.data.base.validation",
		// LLM clients (cidadel 0.4.2+ moved to io.cidadel.client.*)
		"io.cidadel.client.anthropic",
		"io.cidadel.client.openai",
		"io.cidadel.client.grok",
		// AI engine framework (cidadel 0.4.11+)
		"io.cidadel.framework.ai.engine",
		"io.cidadel.data.ai.engine",
		"io.cidadel.business.ai.engine",
		"io.cidadel.service.ai.engine",
		"io.cidadel.client.claudecode",
		"io.cidadel.client.codex"
})
@EnableScheduling
public class TacticlApplication {

	public static void main(String[] args) {
		SpringApplication.run(TacticlApplication.class, args);
	}

}
