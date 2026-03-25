package io.strategiz.social.client.github.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response model for a GitHub file commit (create or update).
 *
 * <p>
 * Maps to GitHub REST API v3 {@code PUT /repos/{owner}/{repo}/contents/{path}} response:
 * <pre>
 * {
 *   "content": { "sha": "...", "path": "...", "name": "..." },
 *   "commit": { "sha": "...", "message": "..." }
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubCommitResult {

	/** The file content entry after the commit. */
	@JsonProperty("content")
	private GitHubFileContent content;

	/** The commit that was created. */
	@JsonProperty("commit")
	private CommitInfo commit;

	public GitHubCommitResult() {
	}

	public GitHubCommitResult(GitHubFileContent content, CommitInfo commit) {
		this.content = content;
		this.commit = commit;
	}

	public GitHubFileContent getContent() {
		return content;
	}

	public void setContent(GitHubFileContent content) {
		this.content = content;
	}

	public CommitInfo getCommit() {
		return commit;
	}

	public void setCommit(CommitInfo commit) {
		this.commit = commit;
	}

	/** Summary of the commit created by the file operation. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class CommitInfo {

		@JsonProperty("sha")
		private String sha;

		@JsonProperty("message")
		private String message;

		@JsonProperty("html_url")
		private String htmlUrl;

		public CommitInfo() {
		}

		public CommitInfo(String sha, String message, String htmlUrl) {
			this.sha = sha;
			this.message = message;
			this.htmlUrl = htmlUrl;
		}

		public String getSha() {
			return sha;
		}

		public void setSha(String sha) {
			this.sha = sha;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		public String getHtmlUrl() {
			return htmlUrl;
		}

		public void setHtmlUrl(String htmlUrl) {
			this.htmlUrl = htmlUrl;
		}

	}

}
