package io.strategiz.social.business.agent.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.cidadel.business.ai.engine.AiStepEngineConfig;
import io.strategiz.social.data.entity.AiSdlcStep;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiSdlcStepDefaultsTest {

	private final AiSdlcStepDefaults defaults = new AiSdlcStepDefaults();

	@Test
	void everyAiSdlcStepHasADefault() {
		for (AiSdlcStep step : AiSdlcStep.values()) {
			Optional<AiStepEngineConfig> config = defaults.getDefault(step.name());
			assertTrue(config.isPresent(), "Missing default for " + step.name());
		}
		assertEquals(AiSdlcStep.values().length, defaults.getAllDefaults().size());
	}

	@Test
	void sparkClassificationUsesAnthropicApiWithHaiku() {
		AiStepEngineConfig config = defaults.getDefault(AiSdlcStep.SPARK_CLASSIFICATION.name()).orElseThrow();
		assertEquals("anthropic-api", config.getEngineId());
		assertEquals("claude-haiku-4-5", config.getModel());
	}

	@Test
	void codeGenerationUsesClaudeCodeCli() {
		AiStepEngineConfig config = defaults.getDefault(AiSdlcStep.CODE_GENERATION.name()).orElseThrow();
		assertEquals("claude-code-cli", config.getEngineId());
		assertEquals("claude-opus-4-6", config.getModel());
	}

	@Test
	void webResearchUsesCodexCli() {
		AiStepEngineConfig config = defaults.getDefault(AiSdlcStep.WEB_RESEARCH.name()).orElseThrow();
		assertEquals("codex-cli", config.getEngineId());
		assertEquals("gpt-5.4", config.getModel());
	}

	@Test
	void agenticStepsHaveFallbacks() {
		Set<String> agenticSteps = Set.of(
				AiSdlcStep.CODE_GENERATION.name(),
				AiSdlcStep.CODE_REFACTORING.name(),
				AiSdlcStep.BUG_DIAGNOSIS.name(),
				AiSdlcStep.BUG_FIX.name(),
				AiSdlcStep.TEST_GENERATION.name(),
				AiSdlcStep.TEST_EXECUTION.name(),
				AiSdlcStep.DEPLOYMENT_SCRIPT.name(),
				AiSdlcStep.WEB_RESEARCH.name()
		);

		for (String step : agenticSteps) {
			AiStepEngineConfig config = defaults.getDefault(step).orElseThrow();
			assertFalse(config.getFallbackEngineIds().isEmpty(),
					"Agentic step " + step + " should have fallback engines");
		}
	}

	@Test
	void unknownStepReturnsEmpty() {
		assertTrue(defaults.getDefault("NONEXISTENT_STEP").isEmpty());
	}

}
