package io.tacticl.business.agent.ai;

import io.cidadel.business.ai.engine.AiEngineStepDefaults;
import io.cidadel.business.ai.engine.AiStepEngineConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tacticl's default AI engine mappings for each SDLC step.
 */
@Component
public class AiSdlcStepDefaults implements AiEngineStepDefaults {

	private static final Map<String, AiStepEngineConfig> DEFAULTS = Map.ofEntries(
			Map.entry("SPARK_CLASSIFICATION",
					new AiStepEngineConfig("anthropic-api", "claude-haiku-4-5", List.of("openai-api", "grok-api"))),
			Map.entry("TASK_DECOMPOSITION",
					new AiStepEngineConfig("anthropic-api", "claude-sonnet-4-5", List.of("openai-api"))),
			Map.entry("CODE_GENERATION",
					new AiStepEngineConfig("claude-code-cli", "claude-opus-4-6", List.of("codex-cli", "anthropic-agentic"))),
			Map.entry("CODE_REVIEW",
					new AiStepEngineConfig("anthropic-agentic", "claude-sonnet-4-5", List.of("anthropic-api", "openai-api"))),
			Map.entry("CODE_REFACTORING",
					new AiStepEngineConfig("claude-code-cli", "claude-sonnet-4-5", List.of("codex-cli", "anthropic-agentic"))),
			Map.entry("BUG_DIAGNOSIS",
					new AiStepEngineConfig("claude-code-cli", "claude-sonnet-4-5", List.of("codex-cli", "anthropic-agentic"))),
			Map.entry("BUG_FIX",
					new AiStepEngineConfig("claude-code-cli", "claude-opus-4-6", List.of("codex-cli", "anthropic-agentic"))),
			Map.entry("TEST_GENERATION",
					new AiStepEngineConfig("claude-code-cli", "claude-sonnet-4-5", List.of("codex-cli", "anthropic-agentic"))),
			Map.entry("TEST_EXECUTION",
					new AiStepEngineConfig("claude-code-cli", "claude-sonnet-4-5", List.of("codex-cli", "anthropic-agentic"))),
			Map.entry("PR_DESCRIPTION",
					new AiStepEngineConfig("anthropic-api", "claude-sonnet-4-5", List.of("openai-api"))),
			Map.entry("DOCUMENTATION",
					new AiStepEngineConfig("anthropic-api", "claude-sonnet-4-5", List.of("openai-api"))),
			Map.entry("COMMIT_MESSAGE",
					new AiStepEngineConfig("anthropic-api", "claude-haiku-4-5", List.of("openai-api"))),
			Map.entry("WEB_RESEARCH",
					new AiStepEngineConfig("codex-cli", "gpt-5.4", List.of("claude-code-cli", "anthropic-agentic"))),
			Map.entry("CODE_ANALYSIS",
					new AiStepEngineConfig("anthropic-agentic", "claude-sonnet-4-5", List.of("anthropic-api", "openai-api"))),
			Map.entry("SOCIAL_CONTENT",
					new AiStepEngineConfig("anthropic-agentic", "claude-sonnet-4-5", List.of("anthropic-api", "openai-api"))),
			Map.entry("CREATIVE_WRITING",
					new AiStepEngineConfig("anthropic-agentic", "claude-sonnet-4-5", List.of("anthropic-api", "openai-api"))),
			Map.entry("IMAGE_ANALYSIS",
					new AiStepEngineConfig("anthropic-api", "claude-sonnet-4-5", List.of("openai-api"))),
			Map.entry("DEPLOYMENT_SCRIPT",
					new AiStepEngineConfig("claude-code-cli", "claude-sonnet-4-5", List.of("codex-cli", "anthropic-agentic"))),
			Map.entry("MONITORING_ANALYSIS",
					new AiStepEngineConfig("anthropic-agentic", "claude-sonnet-4-5", List.of("anthropic-api", "openai-api")))
	);

	@Override
	public Optional<AiStepEngineConfig> getDefault(String stepName) {
		return Optional.ofNullable(DEFAULTS.get(stepName));
	}

	@Override
	public Map<String, AiStepEngineConfig> getAllDefaults() {
		return DEFAULTS;
	}

}
