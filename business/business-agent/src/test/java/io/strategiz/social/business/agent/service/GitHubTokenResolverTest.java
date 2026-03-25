package io.strategiz.social.business.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.strategiz.social.data.entity.PlatformType;
import io.strategiz.social.data.entity.RepoGrant;
import io.strategiz.social.data.entity.SocialIntegration;
import io.strategiz.social.data.repository.RepoGrantRepository;
import io.strategiz.social.data.repository.SocialIntegrationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitHubTokenResolverTest {

	private static final String USER_ID = "user-123";

	private static final String REPO = "owner/my-repo";

	@Mock
	private RepoGrantRepository repoGrantRepository;

	@Mock
	private SocialIntegrationRepository integrationRepository;

	@InjectMocks
	private GitHubTokenResolver resolver;

	// --- Default GitHub integration (no repo grant) ---

	@Test
	void resolve_noRepoGrants_returnsDefaultGitHubToken() {
		when(repoGrantRepository.findActiveByUserId(USER_ID)).thenReturn(List.of());
		SocialIntegration si = activeGitHubIntegration("default-token");
		when(integrationRepository.findByUserIdAndPlatform(USER_ID, PlatformType.GITHUB))
				.thenReturn(Optional.of(si));

		Optional<String> result = resolver.resolve(USER_ID, REPO);

		assertTrue(result.isPresent());
		assertEquals("default-token", result.get());
	}

	@Test
	void resolve_nullRepo_skipsRepoGrantLookupAndReturnsDefault() {
		SocialIntegration si = activeGitHubIntegration("default-token");
		when(integrationRepository.findByUserIdAndPlatform(USER_ID, PlatformType.GITHUB))
				.thenReturn(Optional.of(si));

		Optional<String> result = resolver.resolve(USER_ID, null);

		assertTrue(result.isPresent());
		assertEquals("default-token", result.get());
	}

	@Test
	void resolve_blankRepo_skipsRepoGrantLookupAndReturnsDefault() {
		SocialIntegration si = activeGitHubIntegration("default-token");
		when(integrationRepository.findByUserIdAndPlatform(USER_ID, PlatformType.GITHUB))
				.thenReturn(Optional.of(si));

		Optional<String> result = resolver.resolve(USER_ID, "  ");

		assertTrue(result.isPresent());
		assertEquals("default-token", result.get());
	}

	@Test
	void resolve_noGitHubIntegration_returnsEmpty() {
		when(repoGrantRepository.findActiveByUserId(USER_ID)).thenReturn(List.of());
		when(integrationRepository.findByUserIdAndPlatform(USER_ID, PlatformType.GITHUB))
				.thenReturn(Optional.empty());

		Optional<String> result = resolver.resolve(USER_ID, REPO);

		assertTrue(result.isEmpty());
	}

	@Test
	void resolve_disabledGitHubIntegration_returnsEmpty() {
		when(repoGrantRepository.findActiveByUserId(USER_ID)).thenReturn(List.of());
		SocialIntegration si = activeGitHubIntegration("some-token");
		si.setDisabled(true);
		when(integrationRepository.findByUserIdAndPlatform(USER_ID, PlatformType.GITHUB))
				.thenReturn(Optional.of(si));

		Optional<String> result = resolver.resolve(USER_ID, REPO);

		assertTrue(result.isEmpty());
	}

	@Test
	void resolve_inactiveGitHubIntegration_returnsEmpty() {
		when(repoGrantRepository.findActiveByUserId(USER_ID)).thenReturn(List.of());
		SocialIntegration si = activeGitHubIntegration("some-token");
		si.setIsActive(false);
		when(integrationRepository.findByUserIdAndPlatform(USER_ID, PlatformType.GITHUB))
				.thenReturn(Optional.of(si));

		Optional<String> result = resolver.resolve(USER_ID, REPO);

		assertTrue(result.isEmpty());
	}

	// --- Repo-specific grant with oauthTokenRef ---

	@Test
	void resolve_repoGrantWithTokenRef_returnsRepoSpecificToken() {
		RepoGrant grant = activeRepoGrant(REPO, "integration-id-99");
		when(repoGrantRepository.findActiveByUserId(USER_ID)).thenReturn(List.of(grant));

		SocialIntegration si = activeGitHubIntegration("repo-specific-token");
		when(integrationRepository.findByIdInSubcollection(USER_ID, "integration-id-99"))
				.thenReturn(Optional.of(si));

		Optional<String> result = resolver.resolve(USER_ID, REPO);

		assertTrue(result.isPresent());
		assertEquals("repo-specific-token", result.get());
	}

	@Test
	void resolve_repoGrantWithNullTokenRef_fallsBackToDefault() {
		RepoGrant grant = activeRepoGrant(REPO, null);
		when(repoGrantRepository.findActiveByUserId(USER_ID)).thenReturn(List.of(grant));

		SocialIntegration si = activeGitHubIntegration("default-token");
		when(integrationRepository.findByUserIdAndPlatform(USER_ID, PlatformType.GITHUB))
				.thenReturn(Optional.of(si));

		Optional<String> result = resolver.resolve(USER_ID, REPO);

		assertTrue(result.isPresent());
		assertEquals("default-token", result.get());
	}

	@Test
	void resolve_repoGrantWithBlankTokenRef_fallsBackToDefault() {
		RepoGrant grant = activeRepoGrant(REPO, "");
		when(repoGrantRepository.findActiveByUserId(USER_ID)).thenReturn(List.of(grant));

		SocialIntegration si = activeGitHubIntegration("default-token");
		when(integrationRepository.findByUserIdAndPlatform(USER_ID, PlatformType.GITHUB))
				.thenReturn(Optional.of(si));

		Optional<String> result = resolver.resolve(USER_ID, REPO);

		assertTrue(result.isPresent());
		assertEquals("default-token", result.get());
	}

	@Test
	void resolve_repoGrantTokenRefNotFound_fallsBackToDefault() {
		RepoGrant grant = activeRepoGrant(REPO, "missing-integration-id");
		when(repoGrantRepository.findActiveByUserId(USER_ID)).thenReturn(List.of(grant));
		when(integrationRepository.findByIdInSubcollection(USER_ID, "missing-integration-id"))
				.thenReturn(Optional.empty());

		SocialIntegration si = activeGitHubIntegration("default-token");
		when(integrationRepository.findByUserIdAndPlatform(USER_ID, PlatformType.GITHUB))
				.thenReturn(Optional.of(si));

		Optional<String> result = resolver.resolve(USER_ID, REPO);

		assertTrue(result.isPresent());
		assertEquals("default-token", result.get());
	}

	@Test
	void resolve_repoGrantRefPointsToDisabledIntegration_fallsBackToDefault() {
		RepoGrant grant = activeRepoGrant(REPO, "disabled-integration-id");
		when(repoGrantRepository.findActiveByUserId(USER_ID)).thenReturn(List.of(grant));

		SocialIntegration disabled = activeGitHubIntegration("disabled-token");
		disabled.setDisabled(true);
		when(integrationRepository.findByIdInSubcollection(USER_ID, "disabled-integration-id"))
				.thenReturn(Optional.of(disabled));

		SocialIntegration si = activeGitHubIntegration("default-token");
		when(integrationRepository.findByUserIdAndPlatform(USER_ID, PlatformType.GITHUB))
				.thenReturn(Optional.of(si));

		Optional<String> result = resolver.resolve(USER_ID, REPO);

		assertTrue(result.isPresent());
		assertEquals("default-token", result.get());
	}

	@Test
	void resolve_grantForDifferentRepo_fallsBackToDefault() {
		RepoGrant grant = activeRepoGrant("owner/other-repo", "integration-id-99");
		when(repoGrantRepository.findActiveByUserId(USER_ID)).thenReturn(List.of(grant));

		SocialIntegration si = activeGitHubIntegration("default-token");
		when(integrationRepository.findByUserIdAndPlatform(USER_ID, PlatformType.GITHUB))
				.thenReturn(Optional.of(si));

		Optional<String> result = resolver.resolve(USER_ID, REPO);

		assertTrue(result.isPresent());
		assertEquals("default-token", result.get());
	}

	@Test
	void resolve_repoGrantLookupThrows_fallsBackToDefault() {
		when(repoGrantRepository.findActiveByUserId(USER_ID))
				.thenThrow(new RuntimeException("Firestore unavailable"));

		SocialIntegration si = activeGitHubIntegration("default-token");
		when(integrationRepository.findByUserIdAndPlatform(USER_ID, PlatformType.GITHUB))
				.thenReturn(Optional.of(si));

		Optional<String> result = resolver.resolve(USER_ID, REPO);

		assertTrue(result.isPresent());
		assertEquals("default-token", result.get());
	}

	// --- Helpers ---

	private static SocialIntegration activeGitHubIntegration(String accessToken) {
		SocialIntegration si = new SocialIntegration();
		si.setId("gh-integration-id");
		si.setUserId(USER_ID);
		si.setPlatform(PlatformType.GITHUB);
		si.setAccessToken(accessToken);
		si.setDisabled(false);
		si.setIsActive(true);
		return si;
	}

	private static RepoGrant activeRepoGrant(String repoFullName, String oauthTokenRef) {
		RepoGrant grant = new RepoGrant();
		grant.setId("grant-id-1");
		grant.setUserId(USER_ID);
		grant.setRepoFullName(repoFullName);
		grant.setOauthTokenRef(oauthTokenRef);
		grant.setIsActive(true);
		return grant;
	}

}
