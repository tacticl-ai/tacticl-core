package io.strategiz.social.client.github.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response model for a GitHub repository.
 *
 * <p>
 * Maps to GitHub REST API v3 repository responses from:
 * <ul>
 *   <li>{@code POST /user/repos} — create repo under authenticated user</li>
 *   <li>{@code POST /orgs/{org}/repos} — create repo under org</li>
 * </ul>
 *
 * <p>
 * GitHub returns ~100 fields on a repository; this record exposes the subset relevant
 * to agent-driven provisioning (clone URLs, default branch, visibility). Unknown fields
 * are ignored via {@link JsonIgnoreProperties}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubRepository(
		String name,
		@JsonProperty("full_name") String fullName,
		@JsonProperty("html_url") String htmlUrl,
		@JsonProperty("clone_url") String cloneUrl,
		@JsonProperty("ssh_url") String sshUrl,
		@JsonProperty("private") boolean isPrivate,
		@JsonProperty("default_branch") String defaultBranch) {
}
