package io.strategiz.social.business.agent.ai;

import io.cidadel.business.ai.engine.AiEngineStepDefaults;
import io.cidadel.business.ai.engine.AiStepEngineConfig;
import io.strategiz.social.data.entity.AiSdlcStep;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tacticl's default AI engine mappings for each SDLC step.
 *
 * <p>Maps each {@link AiSdlcStep} to a primary engine + model with ordered fallbacks.
 * Classification steps use cheap API calls; code lifecycle steps prefer agentic CLI
 * engines; content/research steps use standard API engines.</p>
 */
@Component
public class AiSdlcStepDefaults implements AiEngineStepDefaults {

	private static final Map<String, AiStepEngineConfig> DEFAULTS = new HashMap<>();

	static {
		// Classification — cheap, fast API calls
		DEFAULTS.put(AiSdlcStep.SPARK_CLASSIFICATION.name(),
				new AiStepEngineConfig("anthropic-api", "claude-haiku-4-5", List.of("openai-api", "grok-api")));
		DEFAULTS.put(AiSdlcStep.TASK_DECOMPOSITION.name(),
				new AiStepEngineConfig("anthropic-api", "claude-sonnet-4-5", List.of("openai-api")));

		// Code lifecycle — agentic CLI engines
		DEFAULTS.put(AiSdlcStep.CODE_GENERATION.name(),
				new AiStepEngineConfig("claude-code-cli", "claude-opus-4-6", List.of("codex-cli", "anthropic-api")));
		DEFAULTS.put(AiSdlcStep.CODE_REVIEW.name(),
				new AiStepEngineConfig("anthropic-api", "claude-sonnet-4-5", List.of("openai-api")));
		DEFAULTS.put(AiSdlcStep.CODE_REFACTORING.name(),
				new AiStepEngineConfig("claude-code-cli", "claude-sonnet-4-5", List.of("codex-cli")));
		DEFAULTS.put(AiSdlcStep.BUG_DIAGNOSIS.name(),
				new AiStepEngineConfig("claude-code-cli", "claude-sonnet-4-5", List.of("codex-cli")));
		DEFAULTS.put(AiSdlcStep.BUG_FIX.name(),
				new AiStepEngineConfig("claude-code-cli", "claude-opus-4-6", List.of("codex-cli", "anthropic-api")));
		DEFAULTS.put(AiSdlcStep.TEST_GENERATION.name(),
				new AiStepEngineConfig("claude-code-cli", "claude-sonnet-4-5", List.of("codex-cli")));
		DEFAULTS.put(AiSdlcStep.TEST_EXECUTION.name(),
				new AiStepEngineConfig("claude-code-cli", "claude-sonnet-4-5", List.of("codex-cli")));

		// Content — API, no tools needed
		DEFAULTS.put(AiSdlcStep.PR_DESCRIPTION.name(),
				new AiStepEngineConfig("anthropic-api", "claude-sonnet-4-5", List.of("openai-api")));
		DEFAULTS.put(AiSdlcStep.DOCUMENTATION.name(),
				new AiStepEngineConfig("anthropic-api", "claude-sonnet-4-5", List.of("openai-api")));
		DEFAULTS.put(AiSdlcStep.COMMIT_MESSAGE.name(),
				new AiStepEngineConfig("anthropic-api", "claude-haiku-4-5", List.of("openai-api")));

		// Research
		DEFAULTS.put(AiSdlcStep.WEB_RESEARCH.name(),
				new AiStepEngineConfig("codex-cli", "gpt-5.4", List.of("claude-code-cli")));
		DEFAULTS.put(AiSdlcStep.CODE_ANALYSIS.name(),
				new AiStepEngineConfig("anthropic-api", "claude-sonnet-4-5", List.of("openai-api")));

		// Social & Creative
		DEFAULTS.put(AiSdlcStep.SOCIAL_CONTENT.name(),
				new AiStepEngineConfig("anthropic-api", "claude-sonnet-4-5", List.of("openai-api")));
		DEFAULTS.put(AiSdlcStep.CREATIVE_WRITING.name(),
				new AiStepEngineConfig("anthropic-api", "claude-sonnet-4-5", List.of("openai-api")));
		DEFAULTS.put(AiSdlcStep.IMAGE_ANALYSIS.name(),
				new AiStepEngineConfig("anthropic-api", "claude-sonnet-4-5", List.of("openai-api")));

		// DevOps
		DEFAULTS.put(AiSdlcStep.DEPLOYMENT_SCRIPT.name(),
				new AiStepEngineConfig("claude-code-cli", "claude-sonnet-4-5", List.of("codex-cli")));
		DEFAULTS.put(AiSdlcStep.MONITORING_ANALYSIS.name(),
				new AiStepEngineConfig("anthropic-api", "claude-sonnet-4-5", List.of("openai-api")));
	}

	@Override
	public Optional<AiStepEngineConfig> getDefault(String stepName) {
		return Optional.ofNullable(DEFAULTS.get(stepName));
	}

	@Override
	public Map<String, AiStepEngineConfig> getAllDefaults() {
		return Map.copyOf(DEFAULTS);
	}

}
