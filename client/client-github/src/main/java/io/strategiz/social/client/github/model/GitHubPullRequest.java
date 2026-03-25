package io.strategiz.social.client.github.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response model for a GitHub pull request.
 *
 * <p>
 * Maps to GitHub REST API v3 pull request responses from:
 * <ul>
 *   <li>{@code POST /repos/{owner}/{repo}/pulls} — create PR</li>
 *   <li>{@code GET /repos/{owner}/{repo}/pulls/{pull_number}} — get PR</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubPullRequest {

	/** GitHub-assigned pull request number. */
	@JsonProperty("number")
	private int number;

	/** PR title. */
	@JsonProperty("title")
	private String title;

	/** PR description body. */
	@JsonProperty("body")
	private String body;

	/** PR state: {@code open}, {@code closed}, or {@code merged}. */
	@JsonProperty("state")
	private String state;

	/** URL to view the PR on GitHub. */
	@JsonProperty("html_url")
	private String htmlUrl;

	/** The head branch being merged in. */
	@JsonProperty("head")
	private BranchRef head;

	/** The base branch to merge into. */
	@JsonProperty("base")
	private BranchRef base;

	public GitHubPullRequest() {
	}

	public GitHubPullRequest(int number, String title, String body, String state, String htmlUrl,
			BranchRef head, BranchRef base) {
		this.number = number;
		this.title = title;
		this.body = body;
		this.state = state;
		this.htmlUrl = htmlUrl;
		this.head = head;
		this.base = base;
	}

	public int getNumber() {
		return number;
	}

	public void setNumber(int number) {
		this.number = number;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getHtmlUrl() {
		return htmlUrl;
	}

	public void setHtmlUrl(String htmlUrl) {
		this.htmlUrl = htmlUrl;
	}

	public BranchRef getHead() {
		return head;
	}

	public void setHead(BranchRef head) {
		this.head = head;
	}

	public BranchRef getBase() {
		return base;
	}

	public void setBase(BranchRef base) {
		this.base = base;
	}

	/** Branch reference object embedded in PR responses. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class BranchRef {

		@JsonProperty("ref")
		private String ref;

		@JsonProperty("sha")
		private String sha;

		public BranchRef() {
		}

		public BranchRef(String ref, String sha) {
			this.ref = ref;
			this.sha = sha;
		}

		public String getRef() {
			return ref;
		}

		public void setRef(String ref) {
			this.ref = ref;
		}

		public String getSha() {
			return sha;
		}

		public void setSha(String sha) {
			this.sha = sha;
		}

	}

}
