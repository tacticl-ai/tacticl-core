package io.strategiz.social.business.agent.ai;

import io.cidadel.client.anthropic.AnthropicDirectClient;
import io.cidadel.client.base.llm.LlmProvider;
import io.cidadel.client.grok.GrokDirectClient;
import io.cidadel.client.openai.OpenAiDirectClient;
import io.cidadel.framework.ai.engine.AiEngine;
import io.cidadel.framework.ai.engine.AiEngineCostTier;
import io.cidadel.framework.ai.engine.ApiAiEngineAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wraps existing {@link LlmProvider} beans as {@link AiEngine} instances so
 * they are discoverable by the {@link io.cidadel.framework.ai.engine.AiEngineRegistry}.
 *
 * <p>Each LLM provider (anthropic, openai, xai/grok) is conditionally wrapped
 * in an {@link ApiAiEngineAdapter} and registered as an individual bean. The
 * {@code AiEngineRegistry} (component-scanned from cidadel) auto-collects all
 * {@code AiEngine} beans, including these adapters and any CLI-based engines.</p>
 */
@Configuration
public class AiEngineAdapterConfig {

	@Bean
	@ConditionalOnBean(AnthropicDirectClient.class)
	public AiEngine anthropicApiEngine(AnthropicDirectClient client) {
		return new ApiAiEngineAdapter(client, "anthropic-api", "Anthropic API", AiEngineCostTier.MEDIUM);
	}

	@Bean
	@ConditionalOnBean(OpenAiDirectClient.class)
	public AiEngine openaiApiEngine(OpenAiDirectClient client) {
		return new ApiAiEngineAdapter(client, "openai-api", "OpenAI API", AiEngineCostTier.MEDIUM);
	}

	@Bean
	@ConditionalOnBean(GrokDirectClient.class)
	public AiEngine grokApiEngine(GrokDirectClient client) {
		return new ApiAiEngineAdapter(client, "grok-api", "Grok API", AiEngineCostTier.MEDIUM);
	}

}
