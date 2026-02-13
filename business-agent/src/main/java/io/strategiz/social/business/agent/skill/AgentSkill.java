package io.strategiz.social.business.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import io.strategiz.client.base.llm.model.ToolDefinition;

/**
 * Defines a skill (capability) that the voice agent can invoke. Each skill maps to a
 * Claude tool_use tool definition and provides execution logic.
 */
public interface AgentSkill {

	/** Tool name used in Claude tool_use (e.g., "post_to_social"). */
	String getName();

	/** Human-readable description shown to Claude. */
	String getDescription();

	/** Claude tool_use JSON schema for input parameters. */
	ToolDefinition getToolDefinition();

	/** Execute the skill with the given input. Returns a text result for Claude. */
	String execute(JsonNode input, String userId);

	/** Action confirmation tier: 0=auto, 1=confirm, 2=2FA. */
	int getConfirmationTier();

}
