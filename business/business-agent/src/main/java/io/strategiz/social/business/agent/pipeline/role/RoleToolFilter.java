package io.strategiz.social.business.agent.pipeline.role;

import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.agent.service.ToolRegistry;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Filters the global {@link ToolRegistry} to the subset of tools available to a specific
 * PDLC role skill. Each role declares its allowed tools via
 * {@link PdlcRoleSkill#getAvailableTools()}, and this service resolves those names to
 * fully hydrated {@link ToolDefinition} objects.
 */
@Service
public class RoleToolFilter {

	private final ToolRegistry toolRegistry;

	public RoleToolFilter(ToolRegistry toolRegistry) {
		this.toolRegistry = toolRegistry;
	}

	/**
	 * Returns the {@link ToolDefinition} objects that are both registered in the global
	 * {@link ToolRegistry} and listed in the role's allowed tool names.
	 *
	 * @param role the role skill whose available tools should be resolved
	 * @return list of tool definitions scoped to this role; empty if the role has no tools
	 */
	public List<ToolDefinition> getToolDefinitionsForRole(PdlcRoleSkill role) {
		Set<String> allowed = Set.copyOf(role.getAvailableTools());
		if (allowed.isEmpty()) {
			return List.of();
		}
		return toolRegistry.getToolDefinitions().stream()
				.filter(def -> allowed.contains(def.getName()))
				.toList();
	}

}
