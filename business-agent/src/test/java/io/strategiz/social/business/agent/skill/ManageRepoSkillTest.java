package io.strategiz.social.business.agent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strategiz.social.data.entity.AccessLevel;
import io.strategiz.social.data.entity.RepoGrant;
import io.strategiz.social.data.entity.RepoProvider;
import io.strategiz.social.data.repository.RepoGrantRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManageRepoSkillTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Mock
	private RepoGrantRepository repoGrantRepository;

	@InjectMocks
	private ManageRepoSkill skill;

	@Test
	void getName_returnsManageRepo() {
		assertEquals("manage_repo", skill.getName());
	}

	@Test
	void getConfirmationTier_returnsTier1() {
		assertEquals(1, skill.getConfirmationTier());
	}

	// ========== list action ==========

	@Test
	void execute_listAction_noRepos_returnsEmptyMessage() {
		when(repoGrantRepository.findActiveByUserId("user-1")).thenReturn(Collections.emptyList());

		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "list");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("don't have any repositories"));
	}

	@Test
	void execute_listAction_withRepos_returnsFormattedList() {
		RepoGrant grant = new RepoGrant();
		grant.setId("grant-1");
		grant.setRepoFullName("owner/my-repo");
		grant.setProvider(RepoProvider.GITHUB);
		grant.setAccessLevel(AccessLevel.READ);
		when(repoGrantRepository.findActiveByUserId("user-1")).thenReturn(List.of(grant));

		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "list");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("owner/my-repo"));
		assertTrue(result.contains("GITHUB"));
		assertTrue(result.contains("READ"));
		assertTrue(result.contains("grant-1"));
	}

	// ========== grant action ==========

	@Test
	void execute_grantAction_success_savesAndReturnsConfirmation() {
		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "grant");
		input.put("repo_name", "owner/new-repo");
		input.put("provider", "GITLAB");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Repository access granted"));
		assertTrue(result.contains("owner/new-repo"));
		assertTrue(result.contains("GITLAB"));
		assertTrue(result.contains("READ"));
		verify(repoGrantRepository).save(eq("user-1"), any(RepoGrant.class), anyString());
	}

	@Test
	void execute_grantAction_missingRepoName_returnsError() {
		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "grant");
		input.put("provider", "GITHUB");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Missing required field 'repo_name'"));
	}

	@Test
	void execute_grantAction_missingProvider_returnsError() {
		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "grant");
		input.put("repo_name", "owner/repo");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Missing required field 'provider'"));
	}

	@Test
	void execute_grantAction_invalidProvider_returnsError() {
		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "grant");
		input.put("repo_name", "owner/repo");
		input.put("provider", "SVN");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Unknown provider: SVN"));
	}

	// ========== revoke action ==========

	@Test
	void execute_revokeAction_success_softDeletes() {
		RepoGrant grant = new RepoGrant();
		grant.setId("grant-1");
		grant.setRepoFullName("owner/old-repo");
		grant.setProvider(RepoProvider.BITBUCKET);
		grant.setActive(true);
		when(repoGrantRepository.findById("user-1", "grant-1")).thenReturn(Optional.of(grant));

		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "revoke");
		input.put("repo_id", "grant-1");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Repository access revoked"));
		assertTrue(result.contains("owner/old-repo"));
		assertTrue(result.contains("BITBUCKET"));
		verify(repoGrantRepository).save(eq("user-1"), any(RepoGrant.class), eq("grant-1"));
	}

	@Test
	void execute_revokeAction_notFound_returnsError() {
		when(repoGrantRepository.findById("user-1", "missing-id")).thenReturn(Optional.empty());

		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "revoke");
		input.put("repo_id", "missing-id");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Repository grant not found"));
	}

	@Test
	void execute_revokeAction_missingRepoId_returnsError() {
		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "revoke");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Missing required field 'repo_id'"));
	}

	@Test
	void execute_revokeAction_alreadyInactive_returnsNotFound() {
		RepoGrant grant = new RepoGrant();
		grant.setId("grant-2");
		grant.setActive(false);
		when(repoGrantRepository.findById("user-1", "grant-2")).thenReturn(Optional.of(grant));

		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "revoke");
		input.put("repo_id", "grant-2");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Repository grant not found"));
	}

	// ========== unknown action ==========

	@Test
	void execute_unknownAction_returnsError() {
		ObjectNode input = MAPPER.createObjectNode();
		input.put("action", "fork");

		String result = skill.execute(input, "user-1");

		assertTrue(result.contains("Unknown action: fork"));
	}

}
