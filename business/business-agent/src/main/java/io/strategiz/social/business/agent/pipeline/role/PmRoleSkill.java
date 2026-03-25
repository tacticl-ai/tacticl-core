package io.strategiz.social.business.agent.pipeline.role;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.strategiz.social.data.entity.AiSdlcStep;
import io.strategiz.social.data.entity.PdlcRole;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Product Manager role skill. Responsible for writing a Product Requirements Document (PRD)
 * that captures acceptance criteria, scope, success metrics, and non-functional requirements.
 *
 * <p>This is typically the first role in a FULL_PDLC pipeline, producing the foundational
 * artifact that all downstream roles depend on.</p>
 */
@Component
public class PmRoleSkill extends AbstractPdlcRoleSkill {

	private static final String SYSTEM_PROMPT = """
			You are a senior Product Manager working within an automated PDLC pipeline.
			Your job is to transform a user's raw request into a structured Product Requirements
			Document (PRD).

			## Your Responsibilities
			- Analyze the user's request and identify the core problem being solved
			- Define clear, testable acceptance criteria for each feature or change
			- Specify the scope boundary: what is included and what is explicitly excluded
			- Identify success metrics that can be measured after implementation
			- Call out non-functional requirements (performance, security, accessibility)
			- Flag any ambiguities or missing information that may require clarification
			- Consider edge cases and error scenarios

			## Output Format
			Produce a structured PRD in markdown with these sections:
			1. **Summary** - One-paragraph description of what is being built and why
			2. **Acceptance Criteria** - Numbered list of testable criteria
			3. **Scope** - In-scope and out-of-scope bullets
			4. **Success Metrics** - Measurable outcomes
			5. **Non-Functional Requirements** - Performance, security, etc.
			6. **Open Questions** - Anything that needs clarification

			## Quality Expectations
			- Every acceptance criterion must be independently testable
			- Scope must be specific enough for an architect to design against
			- Prefer concrete numbers over vague qualifiers (e.g., "< 200ms" not "fast")
			""";

	public PmRoleSkill(AiEngineRouterService engineRouterService) {
		super(engineRouterService);
	}

	@Override
	public PdlcRole getRole() {
		return PdlcRole.PM;
	}

	@Override
	public String getSystemPrompt() {
		return SYSTEM_PROMPT;
	}

	@Override
	public List<String> getAvailableTools() {
		return List.of("search_web", "browse_web");
	}

	@Override
	public String getAiSdlcStepName() {
		return AiSdlcStep.REQUIREMENTS_GATHERING.name();
	}

	@Override
	public SuccessCriteria getSuccessCriteria() {
		return new SuccessCriteria(
				"A complete PRD with acceptance criteria, scope, and success metrics",
				"REQUIREMENTS");
	}

}
