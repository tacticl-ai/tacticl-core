package io.strategiz.social.business.agent.pipeline.role;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.cidadel.framework.ai.engine.AiEngine;
import io.cidadel.framework.ai.engine.AiEngineRegistry;
import io.cidadel.framework.ai.engine.model.AiEngineRequest;
import io.cidadel.framework.ai.engine.model.AiEngineResult;
import io.cidadel.framework.ai.engine.model.AiEngineToolDefinition;
import io.strategiz.social.data.entity.AiSdlcStep;
import io.strategiz.social.data.entity.PdlcRole;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reviewer role skill. Reviews code produced by the Implementer for quality, patterns,
 * security, and adherence to the architecture. Can REJECT work back to the Implementer
 * with specific feedback for rework.
 */
@Component
public class ReviewerRoleSkill extends AbstractPdlcRoleSkill {

	private static final Logger log = LoggerFactory.getLogger(ReviewerRoleSkill.class);

	private static final String REJECTION_MARKER = "REJECTED";

	private static final String SYSTEM_PROMPT = """
			You are a senior Code Reviewer working within an automated PDLC pipeline.
			Your job is to review code produced by the Implementer role for quality,
			correctness, and adherence to the project's architecture and coding standards.

			## Your Responsibilities
			- Verify the implementation matches the requirements and architecture design
			- Check code quality: readability, naming, structure, DRY principle
			- Validate error handling: proper exception patterns, no swallowed exceptions
			- Confirm adherence to module dependency rules (service -> business -> client -> data)
			- Check for constructor injection (no @Autowired fields)
			- Verify base class usage (BaseController, BaseService, BaseEntity)
			- Look for potential bugs: null pointer risks, race conditions, resource leaks
			- Assess logging: structured messages with context, appropriate log levels

			## Decision: APPROVE or REJECT
			After your review, you must make a clear decision:
			- **APPROVED** - Code meets quality standards and is ready for testing
			- **REJECTED** - Code has issues that must be fixed before proceeding

			## Output Format
			1. **Review Summary** - Overall assessment of the code quality
			2. **Findings** - Numbered list of issues (critical, major, minor, suggestion)
			3. **Decision** - Either "APPROVED" or "REJECTED: <reason>"
			4. **Rework Instructions** - If rejected, specific instructions for the Implementer

			## Quality Expectations
			- Every finding must reference the specific file and line/method
			- Critical and major findings require rejection
			- Minor findings and suggestions do not require rejection
			- Rejection feedback must be actionable (not just "fix this")
			""";

	public ReviewerRoleSkill(AiEngineRouterService engineRouterService, AiEngineRegistry engineRegistry,
			RoleToolFilter roleToolFilter) {
		super(engineRouterService, engineRegistry, roleToolFilter);
	}

	@Override
	public PdlcRole getRole() {
		return PdlcRole.REVIEWER;
	}

	@Override
	public String getSystemPrompt() {
		return SYSTEM_PROMPT;
	}

	@Override
	public List<String> getAvailableTools() {
		return List.of("github_read_file", "github_create_pr", "github_review_pr");
	}

	@Override
	public String getAiSdlcStepName() {
		return AiSdlcStep.CODE_REVIEW.name();
	}

	@Override
	public SuccessCriteria getSuccessCriteria() {
		return new SuccessCriteria(
				"A code review with findings and a clear APPROVED or REJECTED decision",
				"REVIEW");
	}

	@Override
	public RoleResult execute(RoleContext ctx) {
		long start = System.currentTimeMillis();

		AiEngineRequest request = new AiEngineRequest();
		request.setPrompt(buildPrompt(ctx));
		request.setSystemPrompt(getSystemPrompt());
		request.setMetadata(Map.of(
				"sparkId", ctx.childSparkId(),
				"userId", ctx.userId(),
				"pipelineRunId", ctx.pipelineRunId(),
				"pdlcRole", getRole().name()));

		// Apply model override before calling the router (router skips if already set).
		if (ctx.modelOverride() != null) {
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

			if (!result.isSuccess()) {
				log.warn("[PDLC-ROLE] REVIEWER failed for spark={}: {}",
						ctx.childSparkId(), result.getError());
				return RoleResult.failed(result.getError(), metrics);
			}

			String content = result.getContent();

			if (content != null && content.contains(REJECTION_MARKER)) {
				log.info("[PDLC-ROLE] REVIEWER rejected work for spark={}, routing rework to IMPLEMENTER",
						ctx.childSparkId());
				return RoleResult.rejected(content, PdlcRole.IMPLEMENTER, metrics);
			}

			log.info("[PDLC-ROLE] REVIEWER approved for spark={} in {}ms",
					ctx.childSparkId(), duration);
			return RoleResult.completed(
					List.of(Map.of("content", content)),
					content,
					metrics);
		}
		catch (Exception e) {
			long duration = System.currentTimeMillis() - start;
			log.error("[PDLC-ROLE] REVIEWER threw exception for spark={}: {}",
					ctx.childSparkId(), e.getMessage(), e);
			RoleMetrics metrics = new RoleMetrics(0, BigDecimal.ZERO, duration, null, getAiSdlcStepName());
			return RoleResult.failed(e.getMessage(), metrics);
		}
	}

}
