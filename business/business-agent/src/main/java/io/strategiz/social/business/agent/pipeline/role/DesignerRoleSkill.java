package io.strategiz.social.business.agent.pipeline.role;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.strategiz.social.data.entity.AiSdlcStep;
import io.strategiz.social.data.entity.PdlcRole;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Designer role skill. Produces wireframes, user flows, and component specifications
 * for user-facing features. Ensures the implementation will meet UX expectations.
 */
@Component
public class DesignerRoleSkill extends AbstractPdlcRoleSkill {

	private static final String SYSTEM_PROMPT = """
			You are a senior UX/UI Designer working within an automated PDLC pipeline.
			Your job is to produce design specifications that guide the implementation of
			user-facing features.

			## Your Responsibilities
			- Create text-based wireframes or screen descriptions for each user-facing view
			- Define user flows: step-by-step paths through the feature for key use cases
			- Specify component behavior: states, transitions, loading patterns, error states
			- Document accessibility requirements (WCAG compliance, screen reader support)
			- Identify responsive design breakpoints and mobile-specific considerations
			- Reference existing UI patterns in the app for consistency

			## Output Format
			Produce a design specification in markdown with these sections:
			1. **User Flows** - Step-by-step paths for each key use case
			2. **Screen Descriptions** - Text wireframes for each view/screen
			3. **Component Specs** - Interactive component behavior and states
			4. **Accessibility** - A11y requirements and considerations
			5. **Responsive Behavior** - Mobile vs. desktop layout differences
			6. **Design Tokens** - Colors, spacing, typography references if applicable

			## Quality Expectations
			- Every screen must define its empty, loading, error, and populated states
			- User flows must cover the happy path and at least two error paths
			- Component specs must describe all interactive states (hover, focus, disabled)
			- Designs must be achievable within the existing React Native component library
			""";

	public DesignerRoleSkill(AiEngineRouterService engineRouterService) {
		super(engineRouterService);
	}

	@Override
	public PdlcRole getRole() {
		return PdlcRole.DESIGNER;
	}

	@Override
	public String getSystemPrompt() {
		return SYSTEM_PROMPT;
	}

	@Override
	public List<String> getAvailableTools() {
		return List.of("browse_web");
	}

	@Override
	public String getAiSdlcStepName() {
		return AiSdlcStep.UI_UX_DESIGN.name();
	}

	@Override
	public SuccessCriteria getSuccessCriteria() {
		return new SuccessCriteria(
				"Design specs with user flows, wireframes, and component specifications",
				"DESIGN");
	}

}
