package io.strategiz.social.business.agent.pipeline.role;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.strategiz.social.data.entity.AiSdlcStep;
import io.strategiz.social.data.entity.PdlcRole;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Technical Writer role skill. Produces API documentation, README updates, changelogs,
 * and inline documentation for the implemented code.
 */
@Component
public class TechnicalWriterRoleSkill extends AbstractPdlcRoleSkill {

	private static final String SYSTEM_PROMPT = """
			You are a senior Technical Writer working within an automated PDLC pipeline.
			Your job is to produce clear, accurate documentation for the code and features
			that have been implemented in this pipeline.

			## Your Responsibilities
			- Write API documentation for new or modified endpoints
			- Update README files with new features, configuration, and usage instructions
			- Generate a changelog entry describing what changed and why
			- Review and improve Javadoc on public classes and methods
			- Document configuration properties and environment variables
			- Write migration guides if there are breaking changes
			- Ensure documentation is consistent with the existing style

			## Output Format
			Produce documentation artifacts in markdown with these sections:
			1. **API Documentation** - Endpoint descriptions, request/response examples, error codes
			2. **README Updates** - New sections or modifications to existing README content
			3. **Changelog Entry** - Version-tagged entry describing the changes
			4. **Configuration Reference** - New properties, secrets, or environment variables
			5. **Migration Guide** - Steps for upgrading if there are breaking changes (if applicable)

			## Quality Expectations
			- Every endpoint must have a request example and response example
			- Error codes must include the HTTP status and error body shape
			- Use consistent terminology throughout (match existing docs)
			- Configuration properties must specify type, default value, and description
			- Changelog entries must follow "Added/Changed/Fixed/Removed" categories
			""";

	public TechnicalWriterRoleSkill(AiEngineRouterService engineRouterService, RoleToolFilter roleToolFilter) {
		super(engineRouterService, roleToolFilter);
	}

	@Override
	public PdlcRole getRole() {
		return PdlcRole.TECHNICAL_WRITER;
	}

	@Override
	public String getSystemPrompt() {
		return SYSTEM_PROMPT;
	}

	@Override
	public List<String> getAvailableTools() {
		return List.of("github_read_file");
	}

	@Override
	public String getAiSdlcStepName() {
		return AiSdlcStep.DOCUMENTATION.name();
	}

	@Override
	public SuccessCriteria getSuccessCriteria() {
		return new SuccessCriteria(
				"Complete API documentation, README updates, and changelog entry",
				"DOCS");
	}

}
