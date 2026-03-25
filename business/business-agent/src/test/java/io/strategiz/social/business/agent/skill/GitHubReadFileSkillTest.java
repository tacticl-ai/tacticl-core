package io.strategiz.social.business.agent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.client.github.GitHubClient;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitHubReadFileSkillTest {

	private static final JsonMapper MAPPER = new JsonMapper();

	@Mock
	private GitHubClient gitHubClient;

	@Test
	void getName_returnsGitHubReadFile() {
		GitHubReadFileSkill skill = new GitHubReadFileSkill(Optional.of(gitHubClient));
		assertEquals("github_read_file", skill.getName());
	}

	@Test
	void getConfirmationTier_returns0() {
		GitHubReadFileSkill skill = new GitHubReadFileSkill(Optional.of(gitHubClient));
		assertEquals(0, skill.getConfirmationTier());
	}

	@Test
	void getToolDefinition_hasCorrectSchema() {
		GitHubReadFileSkill skill = new GitHubReadFileSkill(Optional.of(gitHubClient));
		ToolDefinition definition = skill.getToolDefinition();

		assertEquals("github_read_file", definition.getName());
		// Schema must require repo and path
		String schemaJson = definition.getInputSchema().toString();
		assertTrue(schemaJson.contains("\"repo\""));
		assertTrue(schemaJson.contains("\"path\""));
		assertTrue(schemaJson.contains("\"branch\""));
		assertTrue(schemaJson.contains("\"required\""));
	}

	@Test
	void execute_clientPresent_returnsFileContent() {
		when(gitHubClient.readFile("owner/repo", "src/Main.java", "main"))
				.thenReturn("public class Main { }");

		GitHubReadFileSkill skill = new GitHubReadFileSkill(Optional.of(gitHubClient));

		ObjectNode input = MAPPER.createObjectNode();
		input.put("repo", "owner/repo");
		input.put("path", "src/Main.java");
		input.put("branch", "main");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("src/Main.java"));
		assertTrue(result.contains("owner/repo"));
		assertTrue(result.contains("public class Main { }"));
	}

	@Test
	void execute_clientPresent_defaultsBranchToMain() {
		when(gitHubClient.readFile("owner/repo", "README.md", "main"))
				.thenReturn("# My Project");

		GitHubReadFileSkill skill = new GitHubReadFileSkill(Optional.of(gitHubClient));

		ObjectNode input = MAPPER.createObjectNode();
		input.put("repo", "owner/repo");
		input.put("path", "README.md");
		// No branch specified — should default to "main"

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("# My Project"));
		assertTrue(result.contains("main"));
	}

	@Test
	void execute_clientEmpty_returnsNotConfiguredMessage() {
		GitHubReadFileSkill skill = new GitHubReadFileSkill(Optional.empty());

		ObjectNode input = MAPPER.createObjectNode();
		input.put("repo", "owner/repo");
		input.put("path", "src/Main.java");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("GitHub integration is not configured"));
	}

	@Test
	void execute_clientThrowsException_returnsErrorMessage() {
		when(gitHubClient.readFile("owner/repo", "missing.txt", "main"))
				.thenThrow(new RuntimeException("404 Not Found"));

		GitHubReadFileSkill skill = new GitHubReadFileSkill(Optional.of(gitHubClient));

		ObjectNode input = MAPPER.createObjectNode();
		input.put("repo", "owner/repo");
		input.put("path", "missing.txt");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Failed to read file"));
		assertTrue(result.contains("missing.txt"));
		assertTrue(result.contains("404 Not Found"));
	}

}
