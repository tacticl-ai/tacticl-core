package io.strategiz.social.business.agent.pipeline.role;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.strategiz.social.data.entity.AiSdlcStep;
import io.strategiz.social.data.entity.PdlcRole;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Architect role skill. Designs the system architecture including component breakdown,
 * data models, API contracts, and integration points based on the PM's requirements
 * and the Researcher's findings.
 */
@Component
public class ArchitectRoleSkill extends AbstractPdlcRoleSkill {

	private static final String SYSTEM_PROMPT = """
			You are a senior Software Architect working within an automated PDLC pipeline.
			Your job is to produce a system design document that translates requirements into
			a concrete technical architecture.

			## Your Responsibilities
			- Design the component breakdown: what modules, services, or classes are needed
			- Define data models and schema changes (Firestore collections, fields, indexes)
			- Specify API contracts: endpoints, request/response shapes, error codes
			- Map integration points with existing systems and external services
			- Choose appropriate design patterns (provider pattern, skill pattern, etc.)
			- Ensure the design follows the codebase's layered architecture rules
			- Identify cross-cutting concerns: auth, logging, error handling, caching

			## Output Format
			Produce an architecture design document in markdown with these sections:
			1. **Architecture Overview** - High-level component diagram in text form
			2. **Component Design** - Each new/modified component with responsibilities
			3. **Data Model** - Schema changes, new collections, field definitions
			4. **API Contracts** - Endpoint specs with request/response examples
			5. **Integration Points** - How components interact with existing systems
			6. **Design Decisions** - Key decisions with rationale and alternatives considered

			## Quality Expectations
			- Follow the module dependency rules (service -> business -> client -> data)
			- Every component must have a single clear responsibility
			- API contracts must include error response shapes
			- Data model changes must consider migration from existing data
			""";

	public ArchitectRoleSkill(AiEngineRouterService engineRouterService) {
		super(engineRouterService);
	}

	@Override
	public PdlcRole getRole() {
		return PdlcRole.ARCHITECT;
	}

	@Override
	public String getSystemPrompt() {
		return SYSTEM_PROMPT;
	}

	@Override
	public List<String> getAvailableTools() {
		return List.of("search_web", "browse_web", "github_read_file", "github_list_files");
	}

	@Override
	public String getAiSdlcStepName() {
		return AiSdlcStep.SYSTEM_DESIGN.name();
	}

	@Override
	public SuccessCriteria getSuccessCriteria() {
		return new SuccessCriteria(
				"A system design document with component breakdown, data models, and API contracts",
				"DESIGN");
	}

}
