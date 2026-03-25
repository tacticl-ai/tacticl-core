package io.strategiz.social.business.agent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import io.cidadel.client.base.llm.model.ToolDefinition;
import io.strategiz.social.business.agent.service.GitHubTokenResolver;
import io.strategiz.social.client.github.GitHubClient;
import io.strategiz.social.client.github.model.GitHubFileContent;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

	@Mock
	private GitHubTokenResolver tokenResolver;

	@Test
	void getName_returnsGitHubReadFile() {
		GitHubReadFileSkill skill = new GitHubReadFileSkill(Optional.of(gitHubClient), tokenResolver);
		assertEquals("github_read_file", skill.getName());
	}

	@Test
	void getConfirmationTier_returns0() {
		GitHubReadFileSkill skill = new GitHubReadFileSkill(Optional.of(gitHubClient), tokenResolver);
		assertEquals(0, skill.getConfirmationTier());
	}

	@Test
	void getToolDefinition_hasCorrectSchema() {
		GitHubReadFileSkill skill = new GitHubReadFileSkill(Optional.of(gitHubClient), tokenResolver);
		ToolDefinition definition = skill.getToolDefinition();

		assertEquals("github_read_file", definition.getName());
		// Schema must require repo and path
		String schemaJson = definition.getInputSchema().toString();
		assertTrue(schemaJson.contains("\"repo\""));
		assertTrue(schemaJson.contains("\"path\""));
		assertTrue(schemaJson.contains("\"branch\""));
		assertTrue(schemaJson.contains("\"required\""));
	}

	private static GitHubFileContent fileContentWith(String rawText) {
		String encoded = Base64.getEncoder().encodeToString(rawText.getBytes(StandardCharsets.UTF_8));
		GitHubFileContent fc = new GitHubFileContent();
		fc.setContent(encoded);
		return fc;
	}

	@Test
	void execute_clientPresent_returnsFileContent() {
		when(tokenResolver.resolve(eq("user-1"), eq("owner/repo"))).thenReturn(Optional.of("gh-token"));
		when(gitHubClient.readFile(eq("owner/repo"), eq("src/Main.java"), eq("main"), eq("gh-token")))
				.thenReturn(fileContentWith("public class Main { }"));

		GitHubReadFileSkill skill = new GitHubReadFileSkill(Optional.of(gitHubClient), tokenResolver);

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
		when(tokenResolver.resolve(eq("user-1"), eq("owner/repo"))).thenReturn(Optional.of("gh-token"));
		when(gitHubClient.readFile(eq("owner/repo"), eq("README.md"), eq("main"), eq("gh-token")))
				.thenReturn(fileContentWith("# My Project"));

		GitHubReadFileSkill skill = new GitHubReadFileSkill(Optional.of(gitHubClient), tokenResolver);

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
		GitHubReadFileSkill skill = new GitHubReadFileSkill(Optional.empty(), tokenResolver);

		ObjectNode input = MAPPER.createObjectNode();
		input.put("repo", "owner/repo");
		input.put("path", "src/Main.java");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("GitHub integration is not configured"));
	}

	@Test
	void execute_noTokenResolved_returnsNoAccessMessage() {
		when(tokenResolver.resolve(eq("user-1"), eq("owner/repo"))).thenReturn(Optional.empty());

		GitHubReadFileSkill skill = new GitHubReadFileSkill(Optional.of(gitHubClient), tokenResolver);

		ObjectNode input = MAPPER.createObjectNode();
		input.put("repo", "owner/repo");
		input.put("path", "src/Main.java");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("No GitHub access configured"));
		assertTrue(result.contains("manage_repo grant"));
	}

	@Test
	void execute_clientThrowsException_returnsErrorMessage() {
		when(tokenResolver.resolve(eq("user-1"), eq("owner/repo"))).thenReturn(Optional.of("gh-token"));
		when(gitHubClient.readFile(eq("owner/repo"), eq("missing.txt"), eq("main"), eq("gh-token")))
				.thenThrow(new RuntimeException("404 Not Found"));

		GitHubReadFileSkill skill = new GitHubReadFileSkill(Optional.of(gitHubClient), tokenResolver);

		ObjectNode input = MAPPER.createObjectNode();
		input.put("repo", "owner/repo");
		input.put("path", "missing.txt");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Failed to read file"));
		assertTrue(result.contains("missing.txt"));
		assertTrue(result.contains("404 Not Found"));
	}

}
