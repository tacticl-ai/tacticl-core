package io.strategiz.social.business.agent.pipeline.role;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.agent.service.ToolRegistry;
import io.strategiz.social.data.entity.PdlcRole;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleToolFilterTest {

	@Mock
	private ToolRegistry toolRegistry;

	private RoleToolFilter filter;

	@BeforeEach
	void setUp() {
		filter = new RoleToolFilter(toolRegistry);
	}

	// --- helpers ---

	private ToolDefinition tool(String name) {
		return new ToolDefinition(name, "desc for " + name, null);
	}

	private PdlcRoleSkill stubRole(PdlcRole role, List<String> availableTools) {
		return new PdlcRoleSkill() {
			@Override public PdlcRole getRole() { return role; }
			@Override public String getSystemPrompt() { return ""; }
			@Override public List<String> getAvailableTools() { return availableTools; }
			@Override public String getAiSdlcStepName() { return ""; }
			@Override public SuccessCriteria getSuccessCriteria() { return new SuccessCriteria("", ""); }
			@Override public RoleResult execute(RoleContext ctx) { return null; }
		};
	}

	// --- filtering ---

	@Test
	void returnsOnlyToolsInAllowedList() {
		when(toolRegistry.getToolDefinitions()).thenReturn(List.of(
				tool("github_read_file"),
				tool("brave_search"),
				tool("post_to_social"),
				tool("github_create_pr")
		));

		PdlcRoleSkill role = stubRole(PdlcRole.RESEARCHER, List.of("brave_search", "github_read_file"));

		List<ToolDefinition> result = filter.getToolDefinitionsForRole(role);

		assertEquals(2, result.size());
		assertTrue(result.stream().anyMatch(t -> "brave_search".equals(t.getName())));
		assertTrue(result.stream().anyMatch(t -> "github_read_file".equals(t.getName())));
	}

	@Test
	void excludesToolsNotInAllowedList() {
		when(toolRegistry.getToolDefinitions()).thenReturn(List.of(
				tool("github_read_file"),
				tool("post_to_social")
		));

		PdlcRoleSkill role = stubRole(PdlcRole.IMPLEMENTER, List.of("github_read_file"));

		List<ToolDefinition> result = filter.getToolDefinitionsForRole(role);

		assertEquals(1, result.size());
		assertEquals("github_read_file", result.get(0).getName());
	}

	@Test
	void returnsEmptyListWhenRoleHasNoTools() {
		// no need to stub toolRegistry — empty allowed list short-circuits before querying
		PdlcRoleSkill role = stubRole(PdlcRole.PM, List.of());

		List<ToolDefinition> result = filter.getToolDefinitionsForRole(role);

		assertTrue(result.isEmpty());
	}

	@Test
	void returnsEmptyListWhenNoneOfAllowedToolsAreRegistered() {
		when(toolRegistry.getToolDefinitions()).thenReturn(List.of(
				tool("some_other_tool")
		));

		PdlcRoleSkill role = stubRole(PdlcRole.TESTER, List.of("nonexistent_tool"));

		List<ToolDefinition> result = filter.getToolDefinitionsForRole(role);

		assertTrue(result.isEmpty());
	}

	@Test
	void returnsAllRegisteredToolsWhenAllAreAllowed() {
		when(toolRegistry.getToolDefinitions()).thenReturn(List.of(
				tool("brave_search"),
				tool("jina_read")
		));

		PdlcRoleSkill role = stubRole(PdlcRole.RESEARCHER, List.of("brave_search", "jina_read"));

		List<ToolDefinition> result = filter.getToolDefinitionsForRole(role);

		assertEquals(2, result.size());
	}

}
