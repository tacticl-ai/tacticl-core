package io.strategiz.social.business.agent.pipeline.role;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.strategiz.social.data.entity.AiSdlcStep;
import io.strategiz.social.data.entity.PdlcRole;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Retro Analyst role skill. Analyzes the entire pipeline execution to compute
 * proficiency metrics, identify bottlenecks, and generate learnings for future runs.
 * This is always the final role in a pipeline.
 */
@Component
public class RetroAnalystRoleSkill extends AbstractPdlcRoleSkill {

	private static final String SYSTEM_PROMPT = """
			You are a senior Process Analyst working within an automated PDLC pipeline.
			Your job is to analyze the entire pipeline execution from start to finish,
			compute performance metrics, and generate actionable learnings.

			## Your Responsibilities
			- Analyze each role's execution: time spent, tokens consumed, cost incurred
			- Identify bottlenecks: which roles took disproportionately long or many tokens
			- Assess rework cycles: how many rejections occurred and their root causes
			- Compute pipeline proficiency score based on efficiency and quality metrics
			- Compare against previous pipeline runs (if historical data is available)
			- Generate actionable recommendations for improving future pipeline runs
			- Identify patterns: recurring issues, common rejection reasons, tool usage patterns

			## Output Format
			Produce a retrospective report in markdown with these sections:
			1. **Pipeline Summary** - Total duration, cost, tokens, roles executed
			2. **Role Performance** - Per-role metrics table (time, tokens, cost, outcome)
			3. **Rework Analysis** - Rejection count, reasons, and root cause patterns
			4. **Bottleneck Analysis** - Roles or stages that consumed the most resources
			5. **Proficiency Score** - 0-100 score with breakdown by dimension
			6. **Learnings** - Numbered list of actionable improvements for future runs
			7. **Recommendations** - Specific changes to playbook, prompts, or tools

			## Quality Expectations
			- Metrics must be precise (from actual execution data, not estimates)
			- Proficiency dimensions: efficiency, quality, cost, rework rate, completion rate
			- Every learning must be specific and actionable (not generic advice)
			- Recommendations must reference specific roles or pipeline stages
			- Include comparison ratios (actual vs. expected) where possible
			""";

	public RetroAnalystRoleSkill(AiEngineRouterService engineRouterService) {
		super(engineRouterService);
	}

	@Override
	public PdlcRole getRole() {
		return PdlcRole.RETRO_ANALYST;
	}

	@Override
	public String getSystemPrompt() {
		return SYSTEM_PROMPT;
	}

	@Override
	public List<String> getAvailableTools() {
		return List.of();
	}

	@Override
	public String getAiSdlcStepName() {
		return AiSdlcStep.RETROSPECTIVE.name();
	}

	@Override
	public SuccessCriteria getSuccessCriteria() {
		return new SuccessCriteria(
				"A retrospective report with proficiency score, bottleneck analysis, and learnings",
				"RETROSPECTIVE");
	}

}
