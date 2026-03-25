package io.strategiz.social.business.agent.pipeline.role;

import io.strategiz.social.data.entity.PdlcRole;
import java.util.List;

/**
 * Contract for a PDLC role skill implementation. Each of the 12 pipeline roles
 * (PM, RESEARCHER, ARCHITECT, etc.) implements this interface to provide its
 * system prompt, tool access, and execution logic.
 *
 * <p>Implementations are Spring beans and are auto-discovered by
 * {@link PdlcRoleRegistry} at startup. A single implementation per {@link PdlcRole}
 * is expected.</p>
 */
public interface PdlcRoleSkill {

	/** The PDLC role this skill implements. */
	PdlcRole getRole();

	/**
	 * The system prompt injected into the AI engine for this role.
	 * Should describe the role's persona, responsibilities, and output expectations.
	 */
	String getSystemPrompt();

	/**
	 * Names of the agent tools (skills) available to this role during execution.
	 * Used by {@link RoleToolFilter} to restrict the tool set for each role.
	 */
	List<String> getAvailableTools();

	/**
	 * The {@link io.strategiz.social.data.entity.AiSdlcStep} name that maps to this role,
	 * used for AI engine routing.
	 */
	String getAiSdlcStepName();

	/** Defines what a successful execution of this role must produce. */
	SuccessCriteria getSuccessCriteria();

	/**
	 * Execute this role with the given context.
	 *
	 * @param ctx all pipeline and role-specific context for this execution
	 * @return the result of this role's execution
	 */
	RoleResult execute(RoleContext ctx);

}
