package io.strategiz.social.business.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "llm.agent")
public class AgentModelConfig {

	private String routingModel = "claude-haiku-4-5";

	private String generationModel = "claude-sonnet-4-5";

	public String getRoutingModel() {
		return this.routingModel;
	}

	public void setRoutingModel(String routingModel) {
		this.routingModel = routingModel;
	}

	public String getGenerationModel() {
		return this.generationModel;
	}

	public void setGenerationModel(String generationModel) {
		this.generationModel = generationModel;
	}

}
