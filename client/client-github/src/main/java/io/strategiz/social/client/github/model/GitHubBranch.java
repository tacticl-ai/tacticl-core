package io.strategiz.social.client.github.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response model for a GitHub branch.
 *
 * <p>
 * Maps to GitHub REST API v3 branch responses, including both the
 * {@code GET /repos/{owner}/{repo}/branches} list entries and the
 * {@code POST /repos/{owner}/{repo}/git/refs} create-ref response.
 *
 * <pre>
 * { "ref": "refs/heads/my-branch", "object": { "sha": "abc123..." } }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubBranch {

	/** Full ref name, e.g. {@code refs/heads/my-branch}. */
	@JsonProperty("ref")
	private String ref;

	/** Branch name (short form), e.g. {@code my-branch}. Populated from list branches response. */
	@JsonProperty("name")
	private String name;

	/** The git object (commit) the branch points to. */
	@JsonProperty("object")
	private GitObject object;

	/** Nested commit sha from the ref object. Populated from list branches response. */
	@JsonProperty("commit")
	private GitObject commit;

	public GitHubBranch() {
	}

	public GitHubBranch(String ref, String name, GitObject object) {
		this.ref = ref;
		this.name = name;
		this.object = object;
	}

	public String getRef() {
		return ref;
	}

	public void setRef(String ref) {
		this.ref = ref;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public GitObject getObject() {
		return object;
	}

	public void setObject(GitObject object) {
		this.object = object;
	}

	public GitObject getCommit() {
		return commit;
	}

	public void setCommit(GitObject commit) {
		this.commit = commit;
	}

	/**
	 * Returns the SHA for this branch — checks both the {@code object} field (used by
	 * refs API) and the {@code commit} field (used by branches list API).
	 */
	public String getSha() {
		if (object != null) {
			return object.getSha();
		}
		if (commit != null) {
			return commit.getSha();
		}
		return null;
	}

	/** Nested git object with sha and type fields. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class GitObject {

		@JsonProperty("sha")
		private String sha;

		@JsonProperty("type")
		private String type;

		public GitObject() {
		}

		public GitObject(String sha, String type) {
			this.sha = sha;
			this.type = type;
		}

		public String getSha() {
			return sha;
		}

		public void setSha(String sha) {
			this.sha = sha;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

	}

}
