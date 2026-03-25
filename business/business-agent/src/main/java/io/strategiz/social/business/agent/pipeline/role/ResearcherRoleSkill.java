package io.strategiz.social.business.agent.pipeline.role;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.strategiz.social.data.entity.AiSdlcStep;
import io.strategiz.social.data.entity.PdlcRole;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Researcher role skill. Investigates the existing codebase, external APIs, prior art,
 * and potential risks to inform downstream roles with evidence-based findings.
 */
@Component
public class ResearcherRoleSkill extends AbstractPdlcRoleSkill {

	private static final String SYSTEM_PROMPT = """
			You are a senior Technical Researcher working within an automated PDLC pipeline.
			Your job is to investigate the codebase, external resources, and prior art to
			produce a research report that informs the architecture and implementation phases.

			## Your Responsibilities
			- Examine the existing codebase for relevant patterns, conventions, and reusable components
			- Research external APIs, libraries, or services that may be needed
			- Identify prior art: has something similar been built before (internally or externally)?
			- Assess technical risks and feasibility concerns
			- Document constraints from the existing architecture (data models, API contracts, dependencies)
			- Investigate security implications and known vulnerabilities in proposed dependencies

			## Output Format
			Produce a research report in markdown with these sections:
			1. **Codebase Analysis** - Relevant existing code, patterns, and conventions found
			2. **External Dependencies** - APIs, libraries, or services to integrate
			3. **Prior Art** - Similar implementations found internally or externally
			4. **Technical Risks** - Feasibility concerns, complexity hotspots, unknowns
			5. **Constraints** - Architectural boundaries, data model limitations
			6. **Recommendations** - Suggested approach based on findings

			## Quality Expectations
			- Cite specific files, classes, or URLs for every finding
			- Distinguish between facts (verified) and assumptions (need validation)
			- Rank risks by severity (critical, high, medium, low)
			- Provide at least two alternative approaches when risks are high
			""";

	public ResearcherRoleSkill(AiEngineRouterService engineRouterService, RoleToolFilter roleToolFilter) {
		super(engineRouterService, roleToolFilter);
	}

	@Override
	public PdlcRole getRole() {
		return PdlcRole.RESEARCHER;
	}

	@Override
	public String getSystemPrompt() {
		return SYSTEM_PROMPT;
	}

	@Override
	public List<String> getAvailableTools() {
		return List.of("search_web", "browse_web", "github_read_file", "github_list_files", "github_search_code");
	}

	@Override
	public String getAiSdlcStepName() {
		return AiSdlcStep.WEB_RESEARCH.name();
	}

	@Override
	public SuccessCriteria getSuccessCriteria() {
		return new SuccessCriteria(
				"A research report with codebase analysis, risks, and recommendations",
				"RESEARCH");
	}

}
