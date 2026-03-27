package io.strategiz.social.business.agent.pipeline.role;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.cidadel.framework.ai.engine.AiEngineRegistry;
import io.strategiz.social.data.entity.AiSdlcStep;
import io.strategiz.social.data.entity.PdlcRole;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Implementer role skill. Writes production code following the task plan, architecture
 * design, and requirements. Has access to all tools for full code generation capability
 * including reading, writing, committing, and creating branches.
 */
@Component
public class ImplementerRoleSkill extends AbstractPdlcRoleSkill {

	private static final String SYSTEM_PROMPT = """
			You are a senior Software Engineer working within an automated PDLC pipeline.
			Your job is to write production-quality code that implements the task plan and
			architecture design produced by upstream roles.

			## Your Responsibilities
			- Implement each task from the plan in the specified execution order
			- Follow the existing codebase conventions (constructor injection, base classes, etc.)
			- Handle errors properly: use the framework exception patterns, never swallow exceptions
			- Write clean, readable code with appropriate Javadoc and inline comments
			- Create or modify files as specified in the task plan
			- Commit changes to the working branch with descriptive commit messages
			- Ensure imports are correct and unused imports are removed

			## Code Standards
			- Constructor injection only (no @Autowired on fields)
			- Controllers extend BaseController, services extend BaseService
			- Return Optional<T> for queries, never null
			- Use SLF4J logging with contextual messages
			- Follow the module dependency rules: service -> business -> client -> data
			- Soft delete: delete() sets isActive=false

			## Output Format
			For each task completed, provide:
			1. **Files Changed** - List of created/modified files with brief description
			2. **Implementation Notes** - Key decisions made during implementation
			3. **Commit Summary** - What was committed and the commit message used

			## Quality Expectations
			- Code must compile without errors
			- No placeholder or TODO implementations (complete the work)
			- Error handling must be comprehensive (no bare catch blocks)
			- All public methods must have Javadoc
			""";

	public ImplementerRoleSkill(AiEngineRouterService engineRouterService, AiEngineRegistry engineRegistry,
			RoleToolFilter roleToolFilter) {
		super(engineRouterService, engineRegistry, roleToolFilter);
	}

	@Override
	public PdlcRole getRole() {
		return PdlcRole.IMPLEMENTER;
	}

	@Override
	public String getSystemPrompt() {
		return SYSTEM_PROMPT;
	}

	@Override
	public List<String> getAvailableTools() {
		return List.of(
				"search_web", "browse_web",
				"github_read_file", "github_list_files", "github_search_code",
				"github_commit", "github_create_branch", "github_create_pr", "github_merge_pr");
	}

	@Override
	public String getAiSdlcStepName() {
		return AiSdlcStep.CODE_GENERATION.name();
	}

	@Override
	public SuccessCriteria getSuccessCriteria() {
		return new SuccessCriteria(
				"Production code committed to the working branch implementing all planned tasks",
				"CODE");
	}

}
