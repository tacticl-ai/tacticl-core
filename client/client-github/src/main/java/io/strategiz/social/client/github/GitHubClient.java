package io.strategiz.social.client.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.cidadel.framework.exception.CidadelException;
import io.github.bucket4j.Bucket;
import io.strategiz.social.client.github.exception.GitHubErrorDetails;
import io.strategiz.social.client.github.model.GitHubBranch;
import io.strategiz.social.client.github.model.GitHubCommitResult;
import io.strategiz.social.client.github.model.GitHubFileContent;
import io.strategiz.social.client.github.model.GitHubPullRequest;
import io.strategiz.social.client.github.model.GitHubRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

/**
 * GitHub REST API v3 client using Spring's {@link RestClient}.
 *
 * <p>
 * All methods require a GitHub personal access token passed as the {@code accessToken} parameter.
 * The token is sent as a {@code Bearer} authorization header on each request.
 *
 * <p>
 * The {@code owner} for all repository-scoped operations is taken from the injected
 * {@code gitHubOwner} configuration value, representing the GitHub user or organization
 * that owns the repositories.
 *
 * <p>
 * Rate limiting is enforced via a {@link Bucket} token bucket. GitHub's REST API rate limit
 * is 5,000 requests/hour for authenticated requests; the default bucket is configured at
 * 80 requests/minute (conservative).
 */
public class GitHubClient {

	private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);

	private static final String MODULE_NAME = "client-github";

	private final RestClient restClient;

	private final Bucket rateLimiter;

	private final String gitHubOwner;

	public GitHubClient(RestClient restClient, Bucket rateLimiter, String gitHubOwner) {
		this.restClient = restClient;
		this.rateLimiter = rateLimiter;
		this.gitHubOwner = gitHubOwner;
	}

	// -------------------------------------------------------------------------
	// Branch operations
	// -------------------------------------------------------------------------

	/**
	 * Create a new branch in a repository from a given commit SHA.
	 * @param repo repository name (without owner prefix)
	 * @param branchName name of the new branch to create
	 * @param baseSha the commit SHA the branch will point to
	 * @param accessToken GitHub personal access token
	 * @return the created branch reference
	 * @throws CidadelException if the request fails or rate limit is exceeded
	 */
	public GitHubBranch createBranch(String repo, String branchName, String baseSha,
			String accessToken) {
		consumeRateLimit();
		log.info("Creating branch '{}' in {}/{} from SHA {}", branchName, gitHubOwner, repo,
				baseSha);

		Map<String, String> body = new HashMap<>();
		body.put("ref", "refs/heads/" + branchName);
		body.put("sha", baseSha);

		try {
			GitHubBranch result = restClient.post()
				.uri("/repos/{owner}/{repo}/git/refs", gitHubOwner, repo)
				.header("Authorization", "Bearer " + accessToken)
				.body(body)
				.retrieve()
				.onStatus(this::isUnauthorized, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Invalid or expired access token");
				})
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.BRANCH_CREATE_FAILED, MODULE_NAME,
							"GitHub API returned status " + res.getStatusCode().value()
									+ " creating branch " + branchName);
				})
				.body(GitHubBranch.class);

			if (result == null) {
				throw new CidadelException(GitHubErrorDetails.BRANCH_CREATE_FAILED, MODULE_NAME,
						"Empty response from GitHub API");
			}

			log.info("Branch '{}' created successfully in {}/{}", branchName, gitHubOwner, repo);
			return result;
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to create branch '{}' in {}/{}: {}", branchName, gitHubOwner, repo,
					e.getMessage(), e);
			throw new CidadelException(GitHubErrorDetails.BRANCH_CREATE_FAILED, MODULE_NAME, e);
		}
	}

	/**
	 * Get the latest commit SHA for a branch.
	 * @param repo repository name (without owner prefix)
	 * @param branch branch name
	 * @param accessToken GitHub personal access token
	 * @return the HEAD commit SHA for the branch
	 * @throws CidadelException if the branch is not found or the request fails
	 */
	public String getLatestCommitSha(String repo, String branch, String accessToken) {
		consumeRateLimit();
		log.info("Fetching latest commit SHA for {}/{} branch '{}'", gitHubOwner, repo, branch);

		try {
			GitHubBranch ref = restClient.get()
				.uri("/repos/{owner}/{repo}/git/ref/heads/{branch}", gitHubOwner, repo, branch)
				.header("Authorization", "Bearer " + accessToken)
				.retrieve()
				.onStatus(this::isUnauthorized, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Invalid or expired access token");
				})
				.onStatus(this::isNotFound, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.NOT_FOUND, MODULE_NAME,
							"Branch not found: " + branch);
				})
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.API_ERROR, MODULE_NAME,
							"GitHub API returned status " + res.getStatusCode().value());
				})
				.body(GitHubBranch.class);

			if (ref == null || ref.getObject() == null || ref.getObject().getSha() == null) {
				throw new CidadelException(GitHubErrorDetails.API_ERROR, MODULE_NAME,
						"Could not extract SHA from ref response for branch: " + branch);
			}

			String sha = ref.getObject().getSha();
			log.info("Latest commit SHA for '{}/{}:{}' is {}", gitHubOwner, repo, branch, sha);
			return sha;
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to get latest commit SHA for {}/{} '{}': {}", gitHubOwner, repo,
					branch, e.getMessage(), e);
			throw new CidadelException(GitHubErrorDetails.API_ERROR, MODULE_NAME, e);
		}
	}

	/**
	 * List all branches for a repository.
	 * @param repo repository name (without owner prefix)
	 * @param accessToken GitHub personal access token
	 * @return list of branches
	 * @throws CidadelException if the request fails
	 */
	public List<GitHubBranch> listBranches(String repo, String accessToken) {
		consumeRateLimit();
		log.info("Listing branches for {}/{}", gitHubOwner, repo);

		try {
			List<GitHubBranch> branches = restClient.get()
				.uri("/repos/{owner}/{repo}/branches", gitHubOwner, repo)
				.header("Authorization", "Bearer " + accessToken)
				.retrieve()
				.onStatus(this::isUnauthorized, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Invalid or expired access token");
				})
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.API_ERROR, MODULE_NAME,
							"GitHub API returned status " + res.getStatusCode().value());
				})
				.body(new ParameterizedTypeReference<List<GitHubBranch>>() {
				});

			List<GitHubBranch> result = branches != null ? branches : new ArrayList<>();
			log.info("Found {} branches in {}/{}", result.size(), gitHubOwner, repo);
			return result;
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to list branches for {}/{}: {}", gitHubOwner, repo, e.getMessage(),
					e);
			throw new CidadelException(GitHubErrorDetails.API_ERROR, MODULE_NAME, e);
		}
	}

	// -------------------------------------------------------------------------
	// File operations
	// -------------------------------------------------------------------------

	/**
	 * Commit a file to a repository (create or update).
	 *
	 * <p>
	 * The {@code content} string is base64-encoded before being sent to GitHub.
	 * When updating an existing file, pass the current file's SHA as {@code sha}.
	 * For new files, pass {@code null} or an empty string for {@code sha}.
	 *
	 * @param repo repository name (without owner prefix)
	 * @param path file path within the repository (e.g. {@code src/main/Foo.java})
	 * @param content raw file content (will be base64-encoded)
	 * @param message commit message
	 * @param branch branch to commit to
	 * @param sha current file SHA (required for updates, null/empty for new files)
	 * @param accessToken GitHub personal access token
	 * @return commit result with content metadata and commit info
	 * @throws CidadelException if the request fails
	 */
	public GitHubCommitResult commitFile(String repo, String path, String content, String message,
			String branch, String sha, String accessToken) {
		consumeRateLimit();
		log.info("Committing file '{}' to {}/{} on branch '{}'", path, gitHubOwner, repo, branch);

		String encodedContent = Base64.getEncoder()
			.encodeToString(content.getBytes(StandardCharsets.UTF_8));

		Map<String, String> body = new HashMap<>();
		body.put("message", message);
		body.put("content", encodedContent);
		body.put("branch", branch);
		if (sha != null && !sha.isBlank()) {
			body.put("sha", sha);
		}

		try {
			GitHubCommitResult result = restClient.put()
				.uri("/repos/{owner}/{repo}/contents/{path}", gitHubOwner, repo, path)
				.header("Authorization", "Bearer " + accessToken)
				.body(body)
				.retrieve()
				.onStatus(this::isUnauthorized, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Invalid or expired access token");
				})
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.COMMIT_FAILED, MODULE_NAME,
							"GitHub API returned status " + res.getStatusCode().value()
									+ " committing file " + path);
				})
				.body(GitHubCommitResult.class);

			if (result == null) {
				throw new CidadelException(GitHubErrorDetails.COMMIT_FAILED, MODULE_NAME,
						"Empty response from GitHub API");
			}

			log.info("File '{}' committed successfully to {}/{} branch '{}'", path, gitHubOwner,
					repo, branch);
			return result;
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to commit file '{}' to {}/{}: {}", path, gitHubOwner, repo,
					e.getMessage(), e);
			throw new CidadelException(GitHubErrorDetails.COMMIT_FAILED, MODULE_NAME, e);
		}
	}

	/**
	 * Read a single file from a repository, with base64 content decoded.
	 * @param repo repository name (without owner prefix)
	 * @param path file path within the repository
	 * @param branch branch name or commit SHA to read from
	 * @param accessToken GitHub personal access token
	 * @return file content entry with decoded content available via {@link GitHubFileContent#getDecodedContent()}
	 * @throws CidadelException if the file is not found or the request fails
	 */
	public GitHubFileContent readFile(String repo, String path, String branch,
			String accessToken) {
		consumeRateLimit();
		log.info("Reading file '{}' from {}/{} at ref '{}'", path, gitHubOwner, repo, branch);

		try {
			GitHubFileContent result = restClient.get()
				.uri("/repos/{owner}/{repo}/contents/{path}?ref={branch}", gitHubOwner, repo, path,
						branch)
				.header("Authorization", "Bearer " + accessToken)
				.retrieve()
				.onStatus(this::isUnauthorized, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Invalid or expired access token");
				})
				.onStatus(this::isNotFound, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.NOT_FOUND, MODULE_NAME,
							"File not found: " + path);
				})
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.FILE_READ_FAILED, MODULE_NAME,
							"GitHub API returned status " + res.getStatusCode().value()
									+ " reading file " + path);
				})
				.body(GitHubFileContent.class);

			if (result == null) {
				throw new CidadelException(GitHubErrorDetails.FILE_READ_FAILED, MODULE_NAME,
						"Empty response from GitHub API");
			}

			log.info("Read file '{}' ({} bytes) from {}/{}", path, result.getSize(), gitHubOwner,
					repo);
			return result;
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to read file '{}' from {}/{}: {}", path, gitHubOwner, repo,
					e.getMessage(), e);
			throw new CidadelException(GitHubErrorDetails.FILE_READ_FAILED, MODULE_NAME, e);
		}
	}

	/**
	 * List files (and directories) at a path within a repository.
	 * @param repo repository name (without owner prefix)
	 * @param path directory path within the repository (empty string for root)
	 * @param branch branch name or commit SHA
	 * @param accessToken GitHub personal access token
	 * @return list of file/directory content entries at the path
	 * @throws CidadelException if the path is not found or the request fails
	 */
	public List<GitHubFileContent> listFiles(String repo, String path, String branch,
			String accessToken) {
		consumeRateLimit();
		log.info("Listing files at '{}' in {}/{} at ref '{}'", path, gitHubOwner, repo, branch);

		try {
			List<GitHubFileContent> result = restClient.get()
				.uri("/repos/{owner}/{repo}/contents/{path}?ref={branch}", gitHubOwner, repo, path,
						branch)
				.header("Authorization", "Bearer " + accessToken)
				.retrieve()
				.onStatus(this::isUnauthorized, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Invalid or expired access token");
				})
				.onStatus(this::isNotFound, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.NOT_FOUND, MODULE_NAME,
							"Path not found: " + path);
				})
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.API_ERROR, MODULE_NAME,
							"GitHub API returned status " + res.getStatusCode().value()
									+ " listing files at " + path);
				})
				.body(new ParameterizedTypeReference<List<GitHubFileContent>>() {
				});

			List<GitHubFileContent> entries = result != null ? result : new ArrayList<>();
			log.info("Found {} entries at '{}' in {}/{}", entries.size(), path, gitHubOwner, repo);
			return entries;
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to list files at '{}' in {}/{}: {}", path, gitHubOwner, repo,
					e.getMessage(), e);
			throw new CidadelException(GitHubErrorDetails.API_ERROR, MODULE_NAME, e);
		}
	}

	// -------------------------------------------------------------------------
	// Pull request operations
	// -------------------------------------------------------------------------

	/**
	 * Create a pull request.
	 * @param repo repository name (without owner prefix)
	 * @param title PR title
	 * @param body PR description body
	 * @param head name of the head branch containing the changes
	 * @param base name of the base branch to merge into
	 * @param accessToken GitHub personal access token
	 * @return the created pull request
	 * @throws CidadelException if the request fails
	 */
	public GitHubPullRequest createPullRequest(String repo, String title, String body, String head,
			String base, String accessToken) {
		consumeRateLimit();
		log.info("Creating PR '{}' in {}/{}: {} → {}", title, gitHubOwner, repo, head, base);

		Map<String, String> requestBody = new HashMap<>();
		requestBody.put("title", title);
		requestBody.put("body", body);
		requestBody.put("head", head);
		requestBody.put("base", base);

		try {
			GitHubPullRequest result = restClient.post()
				.uri("/repos/{owner}/{repo}/pulls", gitHubOwner, repo)
				.header("Authorization", "Bearer " + accessToken)
				.body(requestBody)
				.retrieve()
				.onStatus(this::isUnauthorized, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Invalid or expired access token");
				})
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.PR_CREATE_FAILED, MODULE_NAME,
							"GitHub API returned status " + res.getStatusCode().value()
									+ " creating PR");
				})
				.body(GitHubPullRequest.class);

			if (result == null) {
				throw new CidadelException(GitHubErrorDetails.PR_CREATE_FAILED, MODULE_NAME,
						"Empty response from GitHub API");
			}

			log.info("PR #{} created successfully in {}/{}: {}", result.getNumber(), gitHubOwner,
					repo, result.getHtmlUrl());
			return result;
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to create PR in {}/{}: {}", gitHubOwner, repo, e.getMessage(), e);
			throw new CidadelException(GitHubErrorDetails.PR_CREATE_FAILED, MODULE_NAME, e);
		}
	}

	/**
	 * Get a pull request by number.
	 * @param repo repository name (without owner prefix)
	 * @param prNumber pull request number
	 * @param accessToken GitHub personal access token
	 * @return the pull request
	 * @throws CidadelException if the PR is not found or the request fails
	 */
	public GitHubPullRequest getPullRequest(String repo, int prNumber, String accessToken) {
		consumeRateLimit();
		log.info("Fetching PR #{} from {}/{}", prNumber, gitHubOwner, repo);

		try {
			GitHubPullRequest result = restClient.get()
				.uri("/repos/{owner}/{repo}/pulls/{pull_number}", gitHubOwner, repo, prNumber)
				.header("Authorization", "Bearer " + accessToken)
				.retrieve()
				.onStatus(this::isUnauthorized, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Invalid or expired access token");
				})
				.onStatus(this::isNotFound, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.NOT_FOUND, MODULE_NAME,
							"Pull request not found: #" + prNumber);
				})
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.API_ERROR, MODULE_NAME,
							"GitHub API returned status " + res.getStatusCode().value());
				})
				.body(GitHubPullRequest.class);

			if (result == null) {
				throw new CidadelException(GitHubErrorDetails.API_ERROR, MODULE_NAME,
						"Empty response from GitHub API");
			}

			log.info("Fetched PR #{} '{}' from {}/{}", result.getNumber(), result.getTitle(),
					gitHubOwner, repo);
			return result;
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to get PR #{} from {}/{}: {}", prNumber, gitHubOwner, repo,
					e.getMessage(), e);
			throw new CidadelException(GitHubErrorDetails.API_ERROR, MODULE_NAME, e);
		}
	}

	/**
	 * Submit a review on a pull request.
	 * @param repo repository name (without owner prefix)
	 * @param prNumber pull request number
	 * @param event review event: {@code APPROVE} or {@code REQUEST_CHANGES}
	 * @param body review comment body
	 * @param accessToken GitHub personal access token
	 * @throws CidadelException if the request fails
	 */
	public void reviewPullRequest(String repo, int prNumber, String event, String body,
			String accessToken) {
		consumeRateLimit();
		log.info("Submitting '{}' review on PR #{} in {}/{}", event, prNumber, gitHubOwner, repo);

		Map<String, String> requestBody = new HashMap<>();
		requestBody.put("event", event);
		requestBody.put("body", body);

		try {
			restClient.post()
				.uri("/repos/{owner}/{repo}/pulls/{pull_number}/reviews", gitHubOwner, repo,
						prNumber)
				.header("Authorization", "Bearer " + accessToken)
				.body(requestBody)
				.retrieve()
				.onStatus(this::isUnauthorized, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Invalid or expired access token");
				})
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.PR_REVIEW_FAILED, MODULE_NAME,
							"GitHub API returned status " + res.getStatusCode().value()
									+ " submitting review on PR #" + prNumber);
				})
				.toBodilessEntity();

			log.info("Review '{}' submitted on PR #{} in {}/{}", event, prNumber, gitHubOwner,
					repo);
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to review PR #{} in {}/{}: {}", prNumber, gitHubOwner, repo,
					e.getMessage(), e);
			throw new CidadelException(GitHubErrorDetails.PR_REVIEW_FAILED, MODULE_NAME, e);
		}
	}

	/**
	 * Merge a pull request.
	 * @param repo repository name (without owner prefix)
	 * @param prNumber pull request number
	 * @param mergeMethod merge strategy: {@code merge}, {@code squash}, or {@code rebase}
	 * @param accessToken GitHub personal access token
	 * @throws CidadelException if the merge fails or rate limit is exceeded
	 */
	public void mergePullRequest(String repo, int prNumber, String mergeMethod,
			String accessToken) {
		consumeRateLimit();
		log.info("Merging PR #{} in {}/{} using method '{}'", prNumber, gitHubOwner, repo,
				mergeMethod);

		Map<String, String> requestBody = new HashMap<>();
		requestBody.put("merge_method", mergeMethod);

		try {
			restClient.put()
				.uri("/repos/{owner}/{repo}/pulls/{pull_number}/merge", gitHubOwner, repo, prNumber)
				.header("Authorization", "Bearer " + accessToken)
				.body(requestBody)
				.retrieve()
				.onStatus(this::isUnauthorized, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Invalid or expired access token");
				})
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.PR_MERGE_FAILED, MODULE_NAME,
							"GitHub API returned status " + res.getStatusCode().value()
									+ " merging PR #" + prNumber);
				})
				.toBodilessEntity();

			log.info("PR #{} merged successfully in {}/{}", prNumber, gitHubOwner, repo);
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to merge PR #{} in {}/{}: {}", prNumber, gitHubOwner, repo,
					e.getMessage(), e);
			throw new CidadelException(GitHubErrorDetails.PR_MERGE_FAILED, MODULE_NAME, e);
		}
	}

	// -------------------------------------------------------------------------
	// Repository operations
	// -------------------------------------------------------------------------

	/**
	 * Create a new repository under the given owner.
	 *
	 * <p>
	 * Routes to the GitHub API endpoint based on whether {@code owner} matches the
	 * {@code gitHubOwner} configured on this client:
	 * <ul>
	 *   <li>match (case-insensitive) → {@code POST /user/repos} (authenticated user)</li>
	 *   <li>otherwise → {@code POST /orgs/{owner}/repos}</li>
	 * </ul>
	 *
	 * <p>
	 * Always sends {@code auto_init=true} so the new repo has an initial commit + README
	 * and a usable default branch ({@code main}) for downstream pipeline roles to clone.
	 *
	 * @param name repo name (without owner prefix)
	 * @param owner GitHub user OR org name; if equal to {@code gitHubOwner} the repo is
	 * created under the authenticated user, otherwise under the named org
	 * @param isPrivate visibility flag
	 * @param description optional repo description (may be {@code null})
	 * @param accessToken GitHub personal access token with {@code repo} (+ {@code admin:org}
	 * when owner is an org)
	 * @return the created repository metadata
	 * @throws CidadelException on 401, 403, 404, 422 (name already taken), 5xx, or rate-limit
	 * exhaustion
	 */
	public GitHubRepository createRepo(String name, String owner, boolean isPrivate,
			String description, String accessToken) {
		consumeRateLimit();

		boolean underAuthenticatedUser = owner != null && gitHubOwner != null
				&& owner.equalsIgnoreCase(gitHubOwner);
		log.info("Creating repo '{}' under {} ({})", name, owner,
				underAuthenticatedUser ? "/user/repos" : "/orgs/{owner}/repos");

		Map<String, Object> body = new HashMap<>();
		body.put("name", name);
		body.put("private", isPrivate);
		if (description != null) {
			body.put("description", description);
		}
		// Initialize with a README so the repo has a default branch downstream roles can clone.
		body.put("auto_init", true);

		try {
			RestClient.RequestBodySpec request;
			if (underAuthenticatedUser) {
				request = restClient.post().uri("/user/repos");
			}
			else {
				request = restClient.post().uri("/orgs/{owner}/repos", owner);
			}

			GitHubRepository result = request.header("Authorization", "Bearer " + accessToken)
				.body(body)
				.retrieve()
				.onStatus(this::isUnauthorized, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Invalid or expired access token");
				})
				.onStatus(this::isForbidden, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Token lacks permission to create repos under " + owner
									+ " (need 'repo' + 'admin:org' scopes)");
				})
				.onStatus(this::isNotFound, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.NOT_FOUND, MODULE_NAME,
							"Owner not found or not accessible: " + owner);
				})
				.onStatus(this::isUnprocessableEntity, (req, res) -> {
					String responseBody = readErrorBody(res);
					throw new CidadelException(GitHubErrorDetails.REPO_NAME_TAKEN, MODULE_NAME,
							"GitHub API returned 422 creating repo " + name
									+ " (name may already exist): " + responseBody);
				})
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.REPO_CREATE_FAILED, MODULE_NAME,
							"GitHub API returned status " + res.getStatusCode().value()
									+ " creating repo " + name);
				})
				.body(GitHubRepository.class);

			if (result == null) {
				throw new CidadelException(GitHubErrorDetails.REPO_CREATE_FAILED, MODULE_NAME,
						"Empty response from GitHub API");
			}

			log.info("Repo '{}' created successfully: {}", result.fullName(), result.htmlUrl());
			return result;
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to create repo '{}' under {}: {}", name, owner, e.getMessage(), e);
			throw new CidadelException(GitHubErrorDetails.REPO_CREATE_FAILED, MODULE_NAME, e);
		}
	}

	// -------------------------------------------------------------------------
	// Code search
	// -------------------------------------------------------------------------

	/**
	 * Search for code within a specific repository.
	 * @param repo repository name (without owner prefix)
	 * @param query search query string (e.g. {@code "class Foo"})
	 * @param accessToken GitHub personal access token
	 * @return list of matching file entries (content field not populated in search results)
	 * @throws CidadelException if the request fails
	 */
	public List<GitHubFileContent> searchCode(String repo, String query, String accessToken) {
		consumeRateLimit();
		String fullQuery = query + "+repo:" + gitHubOwner + "/" + repo;
		log.info("Searching code in {}/{} with query: {}", gitHubOwner, repo, query);

		try {
			SearchCodeResponse response = restClient.get()
				.uri("/search/code?q={query}", fullQuery)
				.header("Authorization", "Bearer " + accessToken)
				.retrieve()
				.onStatus(this::isUnauthorized, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.UNAUTHORIZED, MODULE_NAME,
							"Invalid or expired access token");
				})
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new CidadelException(GitHubErrorDetails.SEARCH_FAILED, MODULE_NAME,
							"GitHub API returned status " + res.getStatusCode().value()
									+ " during code search");
				})
				.body(SearchCodeResponse.class);

			List<GitHubFileContent> items = (response != null && response.items != null)
					? response.items : new ArrayList<>();
			log.info("Code search in {}/{} returned {} results", gitHubOwner, repo, items.size());
			return items;
		}
		catch (CidadelException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to search code in {}/{}: {}", gitHubOwner, repo, e.getMessage(), e);
			throw new CidadelException(GitHubErrorDetails.SEARCH_FAILED, MODULE_NAME, e);
		}
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	private void consumeRateLimit() {
		if (!rateLimiter.tryConsume(1)) {
			throw new CidadelException(GitHubErrorDetails.RATE_LIMIT_EXCEEDED, MODULE_NAME,
					"GitHub API rate limit exceeded");
		}
	}

	private boolean isUnauthorized(HttpStatusCode status) {
		return status.value() == HttpStatus.UNAUTHORIZED.value();
	}

	private boolean isNotFound(HttpStatusCode status) {
		return status.value() == HttpStatus.NOT_FOUND.value();
	}

	private boolean isForbidden(HttpStatusCode status) {
		return status.value() == HttpStatus.FORBIDDEN.value();
	}

	private boolean isUnprocessableEntity(HttpStatusCode status) {
		return status.value() == HttpStatus.UNPROCESSABLE_ENTITY.value();
	}

	/** Best-effort read of an error response body for inclusion in exception messages. */
	private String readErrorBody(org.springframework.http.client.ClientHttpResponse res) {
		try {
			byte[] bytes = res.getBody().readAllBytes();
			return new String(bytes, StandardCharsets.UTF_8);
		}
		catch (Exception ignored) {
			return "(no body)";
		}
	}

	/** Wrapper for GitHub code search responses that nest results under an {@code items} key. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class SearchCodeResponse {

		@JsonProperty("total_count")
		int totalCount;

		@JsonProperty("items")
		List<GitHubFileContent> items;

	}

}
