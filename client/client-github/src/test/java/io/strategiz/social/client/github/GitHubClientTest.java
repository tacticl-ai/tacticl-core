package io.strategiz.social.client.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.cidadel.framework.exception.CidadelException;
import io.github.bucket4j.Bucket;
import io.strategiz.social.client.github.model.GitHubBranch;
import io.strategiz.social.client.github.model.GitHubBranch.GitObject;
import io.strategiz.social.client.github.model.GitHubCommitResult;
import io.strategiz.social.client.github.model.GitHubCommitResult.CommitInfo;
import io.strategiz.social.client.github.model.GitHubFileContent;
import io.strategiz.social.client.github.model.GitHubPullRequest;
import io.strategiz.social.client.github.model.GitHubPullRequest.BranchRef;
import io.strategiz.social.client.github.model.GitHubRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestBodyUriSpec;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;
import org.springframework.web.client.RestClient.ResponseSpec;

/**
 * Unit tests for {@link GitHubClient}.
 *
 * <p>
 * Tests verify URL construction, Bearer token header attachment, request body formatting,
 * and response parsing.
 *
 * <p>
 * The {@link RestClient} fluent chain is mocked with {@link Answers#RETURNS_SELF} on the
 * builder mocks ({@code postBodySpec}, {@code putBodySpec}, {@code getBodySpec}), which causes
 * all fluent calls that return the mock's own type (like {@code header()}, {@code body()}) to
 * automatically return the mock itself — the idiomatic way to mock builder-style APIs in Mockito.
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GitHubClientTest {

	private static final String OWNER = "test-owner";

	private static final String REPO = "test-repo";

	private static final String ACCESS_TOKEN = "ghp_testtoken123";

	private static final String BEARER_HEADER = "Bearer " + ACCESS_TOKEN;

	@Mock
	private RestClient restClient;

	@Mock
	private Bucket rateLimiter;

	// GET entry-point spec
	@Mock
	private RequestHeadersUriSpec getUriSpec;

	// GET body/headers spec: RETURNS_SELF so header() returns itself automatically
	@Mock(answer = Answers.RETURNS_SELF)
	private RestClient.RequestHeadersSpec getBodySpec;

	@Mock
	private ResponseSpec getResponseSpec;

	// POST entry-point spec
	@Mock
	private RequestBodyUriSpec postUriSpec;

	// POST body/headers spec: RETURNS_SELF so body() and header() both return itself
	@Mock(answer = Answers.RETURNS_SELF)
	private RestClient.RequestBodySpec postBodySpec;

	@Mock
	private ResponseSpec postResponseSpec;

	// PUT entry-point spec
	@Mock
	private RequestBodyUriSpec putUriSpec;

	// PUT body/headers spec: RETURNS_SELF
	@Mock(answer = Answers.RETURNS_SELF)
	private RestClient.RequestBodySpec putBodySpec;

	@Mock
	private ResponseSpec putResponseSpec;

	private GitHubClient client;

	@BeforeEach
	void setUp() {
		client = new GitHubClient(restClient, rateLimiter, OWNER);
		lenient().when(rateLimiter.tryConsume(1)).thenReturn(true);
	}

	// -------------------------------------------------------------------------
	// Helpers: wire RestClient chain for GET/POST/PUT up to retrieve()
	// -------------------------------------------------------------------------

	/** Wires GET chain for 2-arg URI template (owner + repo). */
	private void stubGet2(String arg1, String arg2) {
		doReturn(getUriSpec).when(restClient).get();
		doReturn(getBodySpec).when(getUriSpec).uri(anyString(), eq(arg1), eq(arg2));
		lenient().when(getBodySpec.retrieve()).thenReturn(getResponseSpec);
		lenient().when(getResponseSpec.onStatus(any(), any())).thenReturn(getResponseSpec);
	}

	/** Wires GET chain for 3-arg URI template (owner + repo + branch/path). */
	private void stubGet3(Object arg1, Object arg2, Object arg3) {
		doReturn(getUriSpec).when(restClient).get();
		doReturn(getBodySpec).when(getUriSpec).uri(anyString(), eq(arg1), eq(arg2), eq(arg3));
		lenient().when(getBodySpec.retrieve()).thenReturn(getResponseSpec);
		lenient().when(getResponseSpec.onStatus(any(), any())).thenReturn(getResponseSpec);
	}

	/** Wires GET chain for 4-arg URI template (owner + repo + path + ref). */
	private void stubGet4(Object arg1, Object arg2, Object arg3, Object arg4) {
		doReturn(getUriSpec).when(restClient).get();
		doReturn(getBodySpec).when(getUriSpec)
			.uri(anyString(), eq(arg1), eq(arg2), eq(arg3), eq(arg4));
		lenient().when(getBodySpec.retrieve()).thenReturn(getResponseSpec);
		lenient().when(getResponseSpec.onStatus(any(), any())).thenReturn(getResponseSpec);
	}

	/** Wires POST chain for 2-arg URI template (owner + repo). */
	private void stubPost2(String arg1, String arg2) {
		doReturn(postUriSpec).when(restClient).post();
		doReturn(postBodySpec).when(postUriSpec).uri(anyString(), eq(arg1), eq(arg2));
		lenient().when(postBodySpec.retrieve()).thenReturn(postResponseSpec);
		lenient().when(postResponseSpec.onStatus(any(), any())).thenReturn(postResponseSpec);
	}

	/** Wires POST chain for 0-arg URI template (e.g. {@code /user/repos}). */
	private void stubPost0() {
		doReturn(postUriSpec).when(restClient).post();
		doReturn(postBodySpec).when(postUriSpec).uri(anyString());
		lenient().when(postBodySpec.retrieve()).thenReturn(postResponseSpec);
		lenient().when(postResponseSpec.onStatus(any(), any())).thenReturn(postResponseSpec);
	}

	/** Wires POST chain for 1-arg URI template (e.g. {@code /orgs/{org}/repos}). */
	private void stubPost1(Object arg1) {
		doReturn(postUriSpec).when(restClient).post();
		doReturn(postBodySpec).when(postUriSpec).uri(anyString(), eq(arg1));
		lenient().when(postBodySpec.retrieve()).thenReturn(postResponseSpec);
		lenient().when(postResponseSpec.onStatus(any(), any())).thenReturn(postResponseSpec);
	}

	/** Wires POST chain for 3-arg URI template (owner + repo + pr number). */
	private void stubPost3(Object arg1, Object arg2, Object arg3) {
		doReturn(postUriSpec).when(restClient).post();
		doReturn(postBodySpec).when(postUriSpec).uri(anyString(), eq(arg1), eq(arg2), eq(arg3));
		lenient().when(postBodySpec.retrieve()).thenReturn(postResponseSpec);
		lenient().when(postResponseSpec.onStatus(any(), any())).thenReturn(postResponseSpec);
	}

	/** Wires PUT chain for 3-arg URI template (owner + repo + path/pr). */
	private void stubPut3(Object arg1, Object arg2, Object arg3) {
		doReturn(putUriSpec).when(restClient).put();
		doReturn(putBodySpec).when(putUriSpec).uri(anyString(), eq(arg1), eq(arg2), eq(arg3));
		lenient().when(putBodySpec.retrieve()).thenReturn(putResponseSpec);
		lenient().when(putResponseSpec.onStatus(any(), any())).thenReturn(putResponseSpec);
	}

	// -------------------------------------------------------------------------
	// createBranch
	// -------------------------------------------------------------------------

	@Test
	void createBranch_success_returnsBranch() {
		GitHubBranch expected = new GitHubBranch("refs/heads/feature-x", null,
				new GitObject("abc123", "commit"));

		stubPost2(OWNER, REPO);
		when(postResponseSpec.body(GitHubBranch.class)).thenReturn(expected);

		GitHubBranch result = client.createBranch(REPO, "feature-x", "abc123", ACCESS_TOKEN);

		assertNotNull(result);
		assertEquals("refs/heads/feature-x", result.getRef());
		assertEquals("abc123", result.getSha());
	}

	@Test
	void createBranch_rateLimitExceeded_throwsCidadelException() {
		when(rateLimiter.tryConsume(1)).thenReturn(false);

		assertThrows(CidadelException.class,
				() -> client.createBranch(REPO, "feature-x", "abc123", ACCESS_TOKEN));
	}

	@Test
	void createBranch_nullResponse_throwsCidadelException() {
		stubPost2(OWNER, REPO);
		when(postResponseSpec.body(GitHubBranch.class)).thenReturn(null);

		assertThrows(CidadelException.class,
				() -> client.createBranch(REPO, "feature-x", "abc123", ACCESS_TOKEN));
	}

	@Test
	void createBranch_setsCorrectRefAndSha() {
		stubPost2(OWNER, REPO);
		// Capture body to assert ref format and SHA
		doReturn(postBodySpec).when(postBodySpec).body(any());
		// Override RETURNS_SELF for body() to capture and assert
		lenient().when(postBodySpec.body(any())).thenAnswer(invocation -> {
			java.util.Map<?, ?> body = invocation.getArgument(0);
			assertEquals("refs/heads/my-branch", body.get("ref"),
					"ref must be refs/heads/<branchName>");
			assertEquals("base-sha", body.get("sha"), "sha must be the baseSha");
			return postBodySpec;
		});
		GitHubBranch stub = new GitHubBranch("refs/heads/my-branch", null,
				new GitObject("base-sha", "commit"));
		when(postResponseSpec.body(GitHubBranch.class)).thenReturn(stub);

		client.createBranch(REPO, "my-branch", "base-sha", ACCESS_TOKEN);
	}

	// -------------------------------------------------------------------------
	// getLatestCommitSha
	// -------------------------------------------------------------------------

	@Test
	void getLatestCommitSha_success_returnsSha() {
		GitHubBranch ref = new GitHubBranch("refs/heads/main", null,
				new GitObject("deadbeef", "commit"));

		stubGet3(OWNER, REPO, "main");
		when(getResponseSpec.body(GitHubBranch.class)).thenReturn(ref);

		String sha = client.getLatestCommitSha(REPO, "main", ACCESS_TOKEN);

		assertEquals("deadbeef", sha);
	}

	@Test
	void getLatestCommitSha_nullObjectField_throwsCidadelException() {
		// Ref with null object.sha → should throw
		GitHubBranch ref = new GitHubBranch("refs/heads/main", null, null);

		stubGet3(OWNER, REPO, "main");
		when(getResponseSpec.body(GitHubBranch.class)).thenReturn(ref);

		assertThrows(CidadelException.class,
				() -> client.getLatestCommitSha(REPO, "main", ACCESS_TOKEN));
	}

	// -------------------------------------------------------------------------
	// listBranches
	// -------------------------------------------------------------------------

	@Test
	void listBranches_success_returnsBranchList() {
		List<GitHubBranch> branches = List.of(new GitHubBranch(null, "main", null),
				new GitHubBranch(null, "develop", null));

		stubGet2(OWNER, REPO);
		doReturn(branches).when(getResponseSpec).body(any(ParameterizedTypeReference.class));

		List<GitHubBranch> result = client.listBranches(REPO, ACCESS_TOKEN);

		assertEquals(2, result.size());
		assertEquals("main", result.get(0).getName());
		assertEquals("develop", result.get(1).getName());
	}

	@Test
	void listBranches_nullResponse_returnsEmptyList() {
		stubGet2(OWNER, REPO);
		doReturn(null).when(getResponseSpec).body(any(ParameterizedTypeReference.class));

		List<GitHubBranch> result = client.listBranches(REPO, ACCESS_TOKEN);

		assertNotNull(result);
		assertEquals(0, result.size());
	}

	// -------------------------------------------------------------------------
	// commitFile
	// -------------------------------------------------------------------------

	@Test
	void commitFile_success_returnsCommitResult() {
		GitHubFileContent fileContent = new GitHubFileContent("Foo.java", "src/Foo.java",
				"newsha456", 100, "file", null, null);
		CommitInfo commitInfo = new CommitInfo("commitsha789", "Add Foo.java", null);
		GitHubCommitResult expected = new GitHubCommitResult(fileContent, commitInfo);

		stubPut3(OWNER, REPO, "src/Foo.java");
		when(putResponseSpec.body(GitHubCommitResult.class)).thenReturn(expected);

		GitHubCommitResult result = client.commitFile(REPO, "src/Foo.java",
				"public class Foo {}", "Add Foo.java", "main", null, ACCESS_TOKEN);

		assertNotNull(result);
		assertNotNull(result.getContent());
		assertEquals("newsha456", result.getContent().getSha());
		assertNotNull(result.getCommit());
		assertEquals("commitsha789", result.getCommit().getSha());
	}

	@Test
	void commitFile_encodesContentAsBase64() {
		String rawContent = "Hello, GitHub!";
		String expectedBase64 = Base64.getEncoder()
			.encodeToString(rawContent.getBytes(StandardCharsets.UTF_8));

		stubPut3(OWNER, REPO, "test.txt");
		// Override RETURNS_SELF body() to capture and assert encoding
		lenient().when(putBodySpec.body(any())).thenAnswer(invocation -> {
			java.util.Map<?, ?> body = invocation.getArgument(0);
			assertEquals(expectedBase64, body.get("content"),
					"Content must be base64-encoded before sending to GitHub API");
			return putBodySpec;
		});
		GitHubCommitResult stub = new GitHubCommitResult(
				new GitHubFileContent("test.txt", "test.txt", "sha", 0, "file", null, null),
				new CommitInfo("csha", "msg", null));
		when(putResponseSpec.body(GitHubCommitResult.class)).thenReturn(stub);

		client.commitFile(REPO, "test.txt", rawContent, "msg", "main", null, ACCESS_TOKEN);
	}

	// -------------------------------------------------------------------------
	// readFile
	// -------------------------------------------------------------------------

	@Test
	void readFile_success_returnsFileContent() {
		String rawText = "public class Foo {}";
		String b64 = Base64.getEncoder().encodeToString(rawText.getBytes(StandardCharsets.UTF_8));
		GitHubFileContent expected = new GitHubFileContent("Foo.java", "src/Foo.java", "sha123",
				rawText.length(), "file", b64, null);

		stubGet4(OWNER, REPO, "src/Foo.java", "main");
		when(getResponseSpec.body(GitHubFileContent.class)).thenReturn(expected);

		GitHubFileContent result = client.readFile(REPO, "src/Foo.java", "main", ACCESS_TOKEN);

		assertNotNull(result);
		assertEquals("Foo.java", result.getName());
		assertEquals("sha123", result.getSha());
		assertEquals(rawText, result.getDecodedContent());
	}

	// -------------------------------------------------------------------------
	// listFiles
	// -------------------------------------------------------------------------

	@Test
	void listFiles_success_returnsEntries() {
		List<GitHubFileContent> entries = List.of(
				new GitHubFileContent("Foo.java", "src/Foo.java", "s1", 100, "file", null, null),
				new GitHubFileContent("Bar.java", "src/Bar.java", "s2", 200, "file", null, null));

		stubGet4(OWNER, REPO, "src", "main");
		doReturn(entries).when(getResponseSpec).body(any(ParameterizedTypeReference.class));

		List<GitHubFileContent> result = client.listFiles(REPO, "src", "main", ACCESS_TOKEN);

		assertEquals(2, result.size());
		assertEquals("Foo.java", result.get(0).getName());
	}

	// -------------------------------------------------------------------------
	// createPullRequest
	// -------------------------------------------------------------------------

	@Test
	void createPullRequest_success_returnsPullRequest() {
		GitHubPullRequest expected = new GitHubPullRequest(42, "feat: add tests",
				"Description here", "open", "https://github.com/owner/repo/pull/42",
				new BranchRef("feature-x", "abc"), new BranchRef("main", "def"));

		stubPost2(OWNER, REPO);
		when(postResponseSpec.body(GitHubPullRequest.class)).thenReturn(expected);

		GitHubPullRequest result = client.createPullRequest(REPO, "feat: add tests",
				"Description here", "feature-x", "main", ACCESS_TOKEN);

		assertNotNull(result);
		assertEquals(42, result.getNumber());
		assertEquals("feat: add tests", result.getTitle());
		assertEquals("open", result.getState());
	}

	@Test
	void createPullRequest_setsCorrectBodyFields() {
		stubPost2(OWNER, REPO);
		lenient().when(postBodySpec.body(any())).thenAnswer(invocation -> {
			java.util.Map<?, ?> body = invocation.getArgument(0);
			assertEquals("My PR", body.get("title"));
			assertEquals("PR description", body.get("body"));
			assertEquals("feature", body.get("head"));
			assertEquals("main", body.get("base"));
			return postBodySpec;
		});
		GitHubPullRequest stub = new GitHubPullRequest(1, "My PR", "PR description", "open",
				null, null, null);
		when(postResponseSpec.body(GitHubPullRequest.class)).thenReturn(stub);

		client.createPullRequest(REPO, "My PR", "PR description", "feature", "main", ACCESS_TOKEN);
	}

	// -------------------------------------------------------------------------
	// getPullRequest
	// -------------------------------------------------------------------------

	@Test
	void getPullRequest_success_returnsPullRequest() {
		GitHubPullRequest expected = new GitHubPullRequest(7, "fix: null pointer", "body", "open",
				"https://github.com/owner/repo/pull/7", null, null);

		stubGet3(OWNER, REPO, 7);
		when(getResponseSpec.body(GitHubPullRequest.class)).thenReturn(expected);

		GitHubPullRequest result = client.getPullRequest(REPO, 7, ACCESS_TOKEN);

		assertNotNull(result);
		assertEquals(7, result.getNumber());
		assertEquals("fix: null pointer", result.getTitle());
	}

	// -------------------------------------------------------------------------
	// reviewPullRequest
	// -------------------------------------------------------------------------

	@Test
	void reviewPullRequest_approve_sendsCorrectEvent() {
		stubPost3(OWNER, REPO, 42);
		lenient().when(postBodySpec.body(any())).thenAnswer(invocation -> {
			java.util.Map<?, ?> body = invocation.getArgument(0);
			assertEquals("APPROVE", body.get("event"), "Review event must be APPROVE");
			assertEquals("LGTM", body.get("body"), "Review body must be passed through");
			return postBodySpec;
		});
		when(postResponseSpec.toBodilessEntity()).thenReturn(null);

		client.reviewPullRequest(REPO, 42, "APPROVE", "LGTM", ACCESS_TOKEN);

		verify(postResponseSpec).toBodilessEntity();
	}

	@Test
	void reviewPullRequest_requestChanges_sendsCorrectEvent() {
		stubPost3(OWNER, REPO, 15);
		lenient().when(postBodySpec.body(any())).thenAnswer(invocation -> {
			java.util.Map<?, ?> body = invocation.getArgument(0);
			assertEquals("REQUEST_CHANGES", body.get("event"),
					"Review event must be REQUEST_CHANGES");
			return postBodySpec;
		});
		when(postResponseSpec.toBodilessEntity()).thenReturn(null);

		client.reviewPullRequest(REPO, 15, "REQUEST_CHANGES", "Needs work", ACCESS_TOKEN);

		verify(postResponseSpec).toBodilessEntity();
	}

	// -------------------------------------------------------------------------
	// mergePullRequest
	// -------------------------------------------------------------------------

	@Test
	void mergePullRequest_squash_sendsSquashMethod() {
		stubPut3(OWNER, REPO, 42);
		lenient().when(putBodySpec.body(any())).thenAnswer(invocation -> {
			java.util.Map<?, ?> body = invocation.getArgument(0);
			assertEquals("squash", body.get("merge_method"), "merge_method must be squash");
			return putBodySpec;
		});
		when(putResponseSpec.toBodilessEntity()).thenReturn(null);

		client.mergePullRequest(REPO, 42, "squash", ACCESS_TOKEN);

		verify(putResponseSpec).toBodilessEntity();
	}

	@Test
	void mergePullRequest_rebase_sendsRebaseMethod() {
		stubPut3(OWNER, REPO, 10);
		lenient().when(putBodySpec.body(any())).thenAnswer(invocation -> {
			java.util.Map<?, ?> body = invocation.getArgument(0);
			assertEquals("rebase", body.get("merge_method"), "merge_method must be rebase");
			return putBodySpec;
		});
		when(putResponseSpec.toBodilessEntity()).thenReturn(null);

		client.mergePullRequest(REPO, 10, "rebase", ACCESS_TOKEN);

		verify(putResponseSpec).toBodilessEntity();
	}

	@Test
	void mergePullRequest_merge_sendsMergeMethod() {
		stubPut3(OWNER, REPO, 5);
		lenient().when(putBodySpec.body(any())).thenAnswer(invocation -> {
			java.util.Map<?, ?> body = invocation.getArgument(0);
			assertEquals("merge", body.get("merge_method"), "merge_method must be merge");
			return putBodySpec;
		});
		when(putResponseSpec.toBodilessEntity()).thenReturn(null);

		client.mergePullRequest(REPO, 5, "merge", ACCESS_TOKEN);

		verify(putResponseSpec).toBodilessEntity();
	}

	// -------------------------------------------------------------------------
	// searchCode
	// -------------------------------------------------------------------------

	@Test
	void searchCode_buildsCorrectQueryWithRepoScope() {
		doReturn(getUriSpec).when(restClient).get();
		when(getUriSpec.uri(anyString(), anyString())).thenAnswer(invocation -> {
			String uriTemplate = invocation.getArgument(0);
			String query = (String) invocation.getArgument(1);
			assertEquals("/search/code?q={query}", uriTemplate,
					"Search must use /search/code endpoint");
			assertEquals("class Foo+repo:" + OWNER + "/" + REPO, query,
					"Query must append repo scope");
			return getBodySpec;
		});
		lenient().when(getBodySpec.retrieve()).thenReturn(getResponseSpec);
		when(getResponseSpec.onStatus(any(), any())).thenReturn(getResponseSpec);
		doReturn(null).when(getResponseSpec).body(any(Class.class));

		List<GitHubFileContent> result = client.searchCode(REPO, "class Foo", ACCESS_TOKEN);

		// null SearchCodeResponse → empty list
		assertNotNull(result);
		assertEquals(0, result.size());
	}

	// -------------------------------------------------------------------------
	// GitHubFileContent.getDecodedContent
	// -------------------------------------------------------------------------

	@Test
	void fileContent_getDecodedContent_decodesBase64WithNewlines() {
		String original = "public class Foo {\n    // body\n}";
		String b64 = Base64.getEncoder().encodeToString(original.getBytes(StandardCharsets.UTF_8));
		// Insert a newline to simulate GitHub's chunking
		String chunked = b64.substring(0, 10) + "\n" + b64.substring(10);

		GitHubFileContent file = new GitHubFileContent();
		file.setContent(chunked);

		assertEquals(original, file.getDecodedContent());
	}

	@Test
	void fileContent_getDecodedContent_nullContent_returnsNull() {
		GitHubFileContent file = new GitHubFileContent();
		file.setContent(null);
		assertEquals(null, file.getDecodedContent());
	}

	@Test
	void fileContent_getDecodedContent_blankContent_returnsNull() {
		GitHubFileContent file = new GitHubFileContent();
		file.setContent("   ");
		assertEquals(null, file.getDecodedContent());
	}

	// -------------------------------------------------------------------------
	// GitHubBranch.getSha
	// -------------------------------------------------------------------------

	@Test
	void branch_getSha_prefersObjectOverCommit() {
		GitHubBranch branch = new GitHubBranch();
		branch.setObject(new GitObject("sha-from-object", "commit"));
		branch.setCommit(new GitObject("sha-from-commit", "commit"));

		assertEquals("sha-from-object", branch.getSha());
	}

	@Test
	void branch_getSha_fallsBackToCommitWhenObjectNull() {
		GitHubBranch branch = new GitHubBranch();
		branch.setCommit(new GitObject("sha-from-commit", "commit"));

		assertEquals("sha-from-commit", branch.getSha());
	}

	@Test
	void branch_getSha_returnsNullWhenBothNull() {
		GitHubBranch branch = new GitHubBranch();
		assertEquals(null, branch.getSha());
	}

	// -------------------------------------------------------------------------
	// createRepo
	// -------------------------------------------------------------------------

	@Test
	void createRepo_underAuthenticatedUser_postsToUserRepos() {
		GitHubRepository expected = new GitHubRepository("new-repo", OWNER + "/new-repo",
				"https://github.com/" + OWNER + "/new-repo",
				"https://github.com/" + OWNER + "/new-repo.git",
				"git@github.com:" + OWNER + "/new-repo.git", true, "main", null);

		stubPost0();
		// Owner matches gitHubOwner config → expect /user/repos URI
		when(postUriSpec.uri(eq("/user/repos"))).thenReturn(postBodySpec);
		when(postResponseSpec.body(GitHubRepository.class)).thenReturn(expected);

		GitHubRepository result = client.createRepo("new-repo", OWNER, true, "A new repo",
				ACCESS_TOKEN);

		assertNotNull(result);
		assertEquals("new-repo", result.name());
		assertEquals(OWNER + "/new-repo", result.fullName());
		assertEquals("https://github.com/" + OWNER + "/new-repo", result.htmlUrl());
		verify(postUriSpec).uri(eq("/user/repos"));
	}

	@Test
	void createRepo_underOrg_postsToOrgRepos() {
		String orgName = "some-org";
		GitHubRepository expected = new GitHubRepository("new-repo", orgName + "/new-repo",
				"https://github.com/" + orgName + "/new-repo",
				"https://github.com/" + orgName + "/new-repo.git",
				"git@github.com:" + orgName + "/new-repo.git", false, "main", null);

		stubPost1(orgName);
		// Owner differs from gitHubOwner config → expect /orgs/{owner}/repos URI
		when(postUriSpec.uri(eq("/orgs/{owner}/repos"), eq(orgName))).thenReturn(postBodySpec);
		when(postResponseSpec.body(GitHubRepository.class)).thenReturn(expected);

		GitHubRepository result = client.createRepo("new-repo", orgName, false, null, ACCESS_TOKEN);

		assertNotNull(result);
		assertEquals("new-repo", result.name());
		assertEquals(orgName + "/new-repo", result.fullName());
		verify(postUriSpec).uri(eq("/orgs/{owner}/repos"), eq(orgName));
	}

	@Test
	void createRepo_passesAutoInitTrue() {
		stubPost0();
		when(postUriSpec.uri(eq("/user/repos"))).thenReturn(postBodySpec);
		lenient().when(postBodySpec.body(any())).thenAnswer(invocation -> {
			java.util.Map<?, ?> body = invocation.getArgument(0);
			assertEquals("new-repo", body.get("name"), "body.name must match");
			assertEquals(Boolean.TRUE, body.get("private"), "body.private must match");
			assertEquals("desc", body.get("description"), "body.description must match");
			assertEquals(Boolean.TRUE, body.get("auto_init"),
					"auto_init must be true so downstream clones have a default branch");
			return postBodySpec;
		});
		GitHubRepository stub = new GitHubRepository("new-repo", OWNER + "/new-repo",
				"https://github.com/" + OWNER + "/new-repo", null, null, true, "main", null);
		when(postResponseSpec.body(GitHubRepository.class)).thenReturn(stub);

		client.createRepo("new-repo", OWNER, true, "desc", ACCESS_TOKEN);
	}

	@Test
	void createRepo_unauthorized_throwsCidadelException() {
		stubPost0();
		when(postUriSpec.uri(eq("/user/repos"))).thenReturn(postBodySpec);
		// Simulate the onStatus(isError, …) error path producing a CidadelException via body()
		when(postResponseSpec.body(GitHubRepository.class))
			.thenThrow(new CidadelException(
					io.strategiz.social.client.github.exception.GitHubErrorDetails.UNAUTHORIZED,
					"client-github", "Invalid or expired access token"));

		assertThrows(CidadelException.class,
				() -> client.createRepo("new-repo", OWNER, true, null, ACCESS_TOKEN));
	}

	@Test
	void createRepo_nameAlreadyExists_throws() {
		stubPost0();
		when(postUriSpec.uri(eq("/user/repos"))).thenReturn(postBodySpec);
		when(postResponseSpec.body(GitHubRepository.class))
			.thenThrow(new CidadelException(
					io.strategiz.social.client.github.exception.GitHubErrorDetails.REPO_NAME_TAKEN,
					"client-github", "GitHub API returned status 422 creating repo new-repo"));

		assertThrows(CidadelException.class,
				() -> client.createRepo("new-repo", OWNER, true, null, ACCESS_TOKEN));
	}

	@Test
	void createRepo_rateLimitExhausted_throws() {
		when(rateLimiter.tryConsume(1)).thenReturn(false);

		assertThrows(CidadelException.class,
				() -> client.createRepo("new-repo", OWNER, true, null, ACCESS_TOKEN));
	}

	@Test
	void createRepo_nullResponse_throwsCidadelException() {
		stubPost0();
		when(postUriSpec.uri(eq("/user/repos"))).thenReturn(postBodySpec);
		when(postResponseSpec.body(GitHubRepository.class)).thenReturn(null);

		assertThrows(CidadelException.class,
				() -> client.createRepo("new-repo", OWNER, true, null, ACCESS_TOKEN));
	}

	@Test
	void createRepo_ownerCaseInsensitiveMatch_postsToUserRepos() {
		// "TEST-OWNER" should match "test-owner" config and route to /user/repos
		GitHubRepository expected = new GitHubRepository("new-repo", OWNER + "/new-repo",
				"https://github.com/" + OWNER + "/new-repo", null, null, true, "main", null);

		stubPost0();
		when(postUriSpec.uri(eq("/user/repos"))).thenReturn(postBodySpec);
		when(postResponseSpec.body(GitHubRepository.class)).thenReturn(expected);

		GitHubRepository result = client.createRepo("new-repo", OWNER.toUpperCase(), true, null,
				ACCESS_TOKEN);

		assertNotNull(result);
		verify(postUriSpec).uri(eq("/user/repos"));
	}

}
