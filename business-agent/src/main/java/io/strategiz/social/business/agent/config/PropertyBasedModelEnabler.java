package io.strategiz.social.business.agent.config;

import java.util.Set;

import io.cidadel.framework.llmrouter.LlmModelEnabler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PropertyBasedModelEnabler implements LlmModelEnabler {

	private final Set<String> disabledModels;

	public PropertyBasedModelEnabler(@Value("${llm.models.disabled:}") String disabledModelsConfig) {
		if (disabledModelsConfig == null || disabledModelsConfig.isBlank()) {
			this.disabledModels = Set.of();
		}
		else {
			this.disabledModels = Set.of(disabledModelsConfig.split(","));
		}
	}

	@Override
	public boolean isModelEnabled(String modelId) {
		return !this.disabledModels.contains(modelId);
	}

}
