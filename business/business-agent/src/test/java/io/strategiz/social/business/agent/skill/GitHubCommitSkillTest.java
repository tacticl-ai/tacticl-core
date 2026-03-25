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
class GitHubCommitSkillTest {

	private static final JsonMapper MAPPER = new JsonMapper();

	@Mock
	private GitHubClient gitHubClient;

	@Test
	void getName_returnsGitHubCommit() {
		GitHubCommitSkill skill = new GitHubCommitSkill(Optional.of(gitHubClient));
		assertEquals("github_commit", skill.getName());
	}

	@Test
	void getConfirmationTier_returns1() {
		GitHubCommitSkill skill = new GitHubCommitSkill(Optional.of(gitHubClient));
		assertEquals(1, skill.getConfirmationTier());
	}

	@Test
	void getToolDefinition_hasCorrectSchema() {
		GitHubCommitSkill skill = new GitHubCommitSkill(Optional.of(gitHubClient));
		ToolDefinition definition = skill.getToolDefinition();

		assertEquals("github_commit", definition.getName());
		String schemaJson = definition.getInputSchema().toString();
		assertTrue(schemaJson.contains("\"repo\""));
		assertTrue(schemaJson.contains("\"path\""));
		assertTrue(schemaJson.contains("\"content\""));
		assertTrue(schemaJson.contains("\"message\""));
		assertTrue(schemaJson.contains("\"branch\""));
		assertTrue(schemaJson.contains("\"required\""));
	}

	@Test
	void execute_clientPresent_returnsCommitSha() {
		when(gitHubClient.commitFile("owner/repo", "src/Hello.java", "public class Hello {}", "Add Hello class", "feature/hello"))
				.thenReturn("abc1234def5678");

		GitHubCommitSkill skill = new GitHubCommitSkill(Optional.of(gitHubClient));

		ObjectNode input = MAPPER.createObjectNode();
		input.put("repo", "owner/repo");
		input.put("path", "src/Hello.java");
		input.put("content", "public class Hello {}");
		input.put("message", "Add Hello class");
		input.put("branch", "feature/hello");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("abc1234def5678"));
		assertTrue(result.contains("owner/repo"));
		assertTrue(result.contains("src/Hello.java"));
		assertTrue(result.contains("feature/hello"));
		assertTrue(result.contains("Add Hello class"));
	}

	@Test
	void execute_clientEmpty_returnsNotConfiguredMessage() {
		GitHubCommitSkill skill = new GitHubCommitSkill(Optional.empty());

		ObjectNode input = MAPPER.createObjectNode();
		input.put("repo", "owner/repo");
		input.put("path", "src/Hello.java");
		input.put("content", "public class Hello {}");
		input.put("message", "Add Hello class");
		input.put("branch", "main");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("GitHub integration is not configured"));
	}

	@Test
	void execute_clientThrowsException_returnsErrorMessage() {
		when(gitHubClient.commitFile("owner/repo", "src/Hello.java", "public class Hello {}", "Add Hello class", "feature/hello"))
				.thenThrow(new RuntimeException("Branch not found"));

		GitHubCommitSkill skill = new GitHubCommitSkill(Optional.of(gitHubClient));

		ObjectNode input = MAPPER.createObjectNode();
		input.put("repo", "owner/repo");
		input.put("path", "src/Hello.java");
		input.put("content", "public class Hello {}");
		input.put("message", "Add Hello class");
		input.put("branch", "feature/hello");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Failed to commit"));
		assertTrue(result.contains("src/Hello.java"));
		assertTrue(result.contains("Branch not found"));
	}

}
