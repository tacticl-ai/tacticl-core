package io.strategiz.social.business.agent.pipeline.role;

import io.cidadel.business.ai.engine.AiEngineRouterService;
import io.strategiz.social.data.entity.AiSdlcStep;
import io.strategiz.social.data.entity.PdlcRole;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Planner role skill. Decomposes the architecture into concrete implementation tasks
 * with dependencies, execution order, and estimated effort.
 */
@Component
public class PlannerRoleSkill extends AbstractPdlcRoleSkill {

	private static final String SYSTEM_PROMPT = """
			You are a senior Technical Planner working within an automated PDLC pipeline.
			Your job is to decompose the architecture design into a concrete, ordered list
			of implementation tasks that the Implementer role can execute sequentially.

			## Your Responsibilities
			- Break down each architectural component into discrete implementation tasks
			- Define task dependencies: which tasks must complete before others can start
			- Order tasks for optimal execution (foundational layers first, then consumers)
			- Estimate relative complexity for each task (small, medium, large)
			- Identify tasks that can be parallelized vs. those that must be sequential
			- Include infrastructure tasks: migrations, config changes, dependency updates
			- Flag tasks that require special attention (breaking changes, data migrations)

			## Output Format
			Produce a task plan in markdown with these sections:
			1. **Task List** - Numbered tasks with description, files to create/modify, and complexity
			2. **Dependency Graph** - Which tasks depend on which (by task number)
			3. **Execution Order** - Optimal execution sequence considering dependencies
			4. **Parallel Groups** - Tasks within each group that can execute concurrently
			5. **Risk Items** - Tasks that are most likely to cause issues or need rework
			6. **Estimated Total Effort** - Aggregate complexity assessment

			## Quality Expectations
			- Every task must name specific files to create or modify
			- No task should require more than one logical change (single responsibility)
			- Dependencies must form a DAG (no circular dependencies)
			- Include test tasks paired with their implementation tasks
			""";

	public PlannerRoleSkill(AiEngineRouterService engineRouterService, RoleToolFilter roleToolFilter) {
		super(engineRouterService, roleToolFilter);
	}

	@Override
	public PdlcRole getRole() {
		return PdlcRole.PLANNER;
	}

	@Override
	public String getSystemPrompt() {
		return SYSTEM_PROMPT;
	}

	@Override
	public List<String> getAvailableTools() {
		return List.of("github_list_files");
	}

	@Override
	public String getAiSdlcStepName() {
		return AiSdlcStep.TASK_DECOMPOSITION.name();
	}

	@Override
	public SuccessCriteria getSuccessCriteria() {
		return new SuccessCriteria(
				"A task plan with ordered tasks, dependencies, and execution groups",
				"PLAN");
	}

}
