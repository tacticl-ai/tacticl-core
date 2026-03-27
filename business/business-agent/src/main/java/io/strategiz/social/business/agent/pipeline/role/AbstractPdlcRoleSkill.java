package io.strategiz.social.business.agent.pipeline.role;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.cidadel.framework.ai.engine.AiEngine;
import io.cidadel.framework.ai.engine.AiEngineRegistry;
import io.cidadel.framework.ai.engine.model.AiEngineRequest;
import io.cidadel.framework.ai.engine.model.AiEngineResult;
import io.cidadel.framework.ai.engine.model.AiEngineToolDefinition;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineArtifact;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared base class for all 12 PDLC role skill implementations. Provides:
 * <ul>
 *   <li>Engine execution via {@link AiEngineRouterService}</li>
 *   <li>Prompt construction with upstream artifacts and rework feedback</li>
 *   <li>Cost estimation from token counts</li>
 * </ul>
 *
 * <p>Subclasses must implement the interface methods from {@link PdlcRoleSkill}
 * (role, system prompt, tools, SDLC step, success criteria) and may override
 * {@link #execute(RoleContext)} to add role-specific logic around the engine call.</p>
 */
public abstract class AbstractPdlcRoleSkill implements PdlcRoleSkill {

	private static final Logger log = LoggerFactory.getLogger(AbstractPdlcRoleSkill.class);

	/**
	 * Approximate cost per 1K tokens (blended input/output) used for estimation.
	 * Actual cost varies by model; this is a conservative average across Haiku/Sonnet.
	 */
	private static final BigDecimal COST_PER_1K_TOKENS = new BigDecimal("0.008");

	protected final AiEngineRouterService engineRouterService;

	protected final AiEngineRegistry engineRegistry;

	protected final RoleToolFilter roleToolFilter;

	protected AbstractPdlcRoleSkill(AiEngineRouterService engineRouterService,
			AiEngineRegistry engineRegistry, RoleToolFilter roleToolFilter) {
		this.engineRouterService = engineRouterService;
		this.engineRegistry = engineRegistry;
		this.roleToolFilter = roleToolFilter;
	}

	@Override
	public RoleResult execute(RoleContext ctx) {
		return executeWithEngine(ctx);
	}

	/**
	 * Execute this role via the AI engine router. Builds the prompt from context,
	 * sends it through the engine for this role's SDLC step, and wraps the result.
	 *
	 * <p>If {@code ctx} carries a role-level override (set by admin via
	 * {@link io.strategiz.social.business.agent.ai.AiRoleOverrideService}), those values
	 * take precedence over step-level defaults:
	 * <ul>
	 *   <li>{@code modelOverride} — applied directly on the {@link AiEngineRequest}; the
	 *       engine router skips its own model assignment when the field is already set.</li>
	 *   <li>{@code engineIdOverride} — bypasses the normal step-routing and calls the named
	 *       engine directly from {@link AiEngineRegistry}.</li>
	 * </ul>
	 *
	 * @param ctx the role execution context
	 * @return the role result with metrics
	 */
	protected RoleResult executeWithEngine(RoleContext ctx) {
		long start = System.currentTimeMillis();

		AiEngineRequest request = new AiEngineRequest();
		request.setPrompt(buildPrompt(ctx));
		request.setSystemPrompt(getSystemPrompt());
		request.setMetadata(Map.of(
				"sparkId", ctx.childSparkId(),
				"userId", ctx.userId(),
				"pipelineRunId", ctx.pipelineRunId(),
				"pdlcRole", getRole().name()));

		// Apply model override before calling the router — the router will not overwrite
		// a model that is already set on the request (see AiEngineRouterService.executeStep).
		if (ctx.modelOverride() != null) {
			log.debug("[PDLC-ROLE] Applying model override '{}' for role={}", ctx.modelOverride(), getRole());
			request.setModel(ctx.modelOverride());
		}

		List<ToolDefinition> roleTools = roleToolFilter.getToolDefinitionsForRole(this);
		if (!roleTools.isEmpty()) {
			List<AiEngineToolDefinition> engineTools = roleTools.stream()
					.map(t -> new AiEngineToolDefinition(t.getName(), t.getDescription(), t.getInputSchema()))
					.toList();
			request.setTools(engineTools);
		}

		try {
			AiEngineResult result;
			if (ctx.engineIdOverride() != null) {
				// Engine override: bypass step routing and call the named engine directly.
				log.info("[PDLC-ROLE] Applying engine override '{}' for role={}", ctx.engineIdOverride(), getRole());
				Optional<AiEngine> engineOpt = engineRegistry.getEngine(ctx.engineIdOverride());
				AiEngine engine = engineOpt.orElseThrow(() -> new IllegalStateException(
						"Role override engine not found: " + ctx.engineIdOverride()));
				result = engine.execute(request);
			}
			else {
				result = engineRouterService.executeStep(getAiSdlcStepName(), request);
			}
			long duration = System.currentTimeMillis() - start;

			RoleMetrics metrics = new RoleMetrics(
					result.getTotalTokens(),
					estimateCost(result.getTotalTokens(), result.getModel()),
					duration,
					result.getModel(),
					getAiSdlcStepName());

			if (result.isSuccess()) {
				log.info("[PDLC-ROLE] {} completed for spark={} in {}ms ({}tokens)",
						getRole(), ctx.childSparkId(), duration, result.getTotalTokens());
				return RoleResult.completed(
						List.of(Map.of("content", result.getContent())),
						result.getContent(),
						metrics);
			}
			else {
				log.warn("[PDLC-ROLE] {} failed for spark={}: {}",
						getRole(), ctx.childSparkId(), result.getError());
				return RoleResult.failed(result.getError(), metrics);
			}
		}
		catch (Exception e) {
			long duration = System.currentTimeMillis() - start;
			log.error("[PDLC-ROLE] {} threw exception for spark={}: {}",
					getRole(), ctx.childSparkId(), e.getMessage(), e);
			RoleMetrics metrics = new RoleMetrics(0, BigDecimal.ZERO, duration, null, getAiSdlcStepName());
			return RoleResult.failed(e.getMessage(), metrics);
		}
	}

	/**
	 * Build the user prompt from context, including the original request,
	 * rework feedback (if any), and summaries of upstream role artifacts.
	 *
	 * @param ctx the role execution context
	 * @return the assembled prompt string
	 */
	protected String buildPrompt(RoleContext ctx) {
		StringBuilder sb = new StringBuilder();

		sb.append("## User Request\n").append(ctx.originalRequest()).append("\n\n");

		if (ctx.reworkFeedback() != null) {
			sb.append("## Rework Required (Iteration ").append(ctx.reworkIteration()).append(")\n");
			sb.append(ctx.reworkFeedback()).append("\n\n");
		}

		if (ctx.upstreamArtifacts() != null && !ctx.upstreamArtifacts().isEmpty()) {
			sb.append("## Previous Role Outputs\n");
			for (Map.Entry<PdlcRole, PipelineArtifact> entry : ctx.upstreamArtifacts().entrySet()) {
				sb.append("### ").append(entry.getKey().name()).append("\n");
				PipelineArtifact artifact = entry.getValue();
				if (artifact.getContent() != null) {
					Object summary = artifact.getContent().get("summary");
					if (summary != null) {
						sb.append(summary);
					}
					else {
						Object content = artifact.getContent().get("content");
						if (content != null) {
							sb.append(content);
						}
					}
				}
				sb.append("\n\n");
			}
		}

		if (ctx.gitContext() != null) {
			sb.append("## Git Context\n");
			sb.append("- Repository: ").append(ctx.gitContext().repoFullName()).append("\n");
			sb.append("- Base branch: ").append(ctx.gitContext().baseBranch()).append("\n");
			sb.append("- Working branch: ").append(ctx.gitContext().workingBranch()).append("\n");
			if (ctx.gitContext().latestCommitSha() != null) {
				sb.append("- Latest commit: ").append(ctx.gitContext().latestCommitSha()).append("\n");
			}
			sb.append("\n");
		}

		return sb.toString();
	}

	/**
	 * Estimate cost in USD from token count and model identifier.
	 *
	 * @param totalTokens the total tokens consumed
	 * @param model the model used (currently unused but available for model-specific pricing)
	 * @return estimated cost in USD
	 */
	protected BigDecimal estimateCost(long totalTokens, String model) {
		if (totalTokens <= 0) {
			return BigDecimal.ZERO;
		}
		return COST_PER_1K_TOKENS
				.multiply(BigDecimal.valueOf(totalTokens))
				.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
	}

}
