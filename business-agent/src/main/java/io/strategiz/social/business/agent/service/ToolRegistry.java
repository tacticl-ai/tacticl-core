package io.strategiz.social.business.agent.service;

import io.strategiz.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.agent.skill.AgentSkill;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registry of all available agent skills. Maps tool names to AgentSkill implementations
 * and provides tool definitions for Claude.
 */
@Component
public class ToolRegistry {

	private static final Logger logger = LoggerFactory.getLogger(ToolRegistry.class);

	private final Map<String, AgentSkill> skills = new LinkedHashMap<>();

	/** Register skills at construction time via Spring injection. */
	public ToolRegistry(List<AgentSkill> skillList) {
		for (AgentSkill skill : skillList) {
			skills.put(skill.getName(), skill);
			logger.info("Registered agent skill: {} (tier {})", skill.getName(), skill.getConfirmationTier());
		}
		logger.info("ToolRegistry initialized with {} skills", skills.size());
	}

	/** Get a skill by name. */
	public Optional<AgentSkill> getSkill(String name) {
		return Optional.ofNullable(skills.get(name));
	}

	/** Get all registered skills. */
	public Collection<AgentSkill> getAllSkills() {
		return skills.values();
	}

	/** Get tool definitions for Claude (all registered skills). */
	public List<ToolDefinition> getToolDefinitions() {
		return skills.values().stream().map(AgentSkill::getToolDefinition).toList();
	}

	/** Check if a skill exists. */
	public boolean hasSkill(String name) {
		return skills.containsKey(name);
	}

}
