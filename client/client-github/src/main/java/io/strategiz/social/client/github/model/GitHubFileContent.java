package io.strategiz.social.client.github.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Response model for a GitHub file content entry.
 *
 * <p>
 * Maps to GitHub REST API v3 contents responses from:
 * <ul>
 *   <li>{@code GET /repos/{owner}/{repo}/contents/{path}} — single file or directory listing</li>
 *   <li>{@code GET /search/code} — code search result items</li>
 * </ul>
 *
 * <p>
 * When {@code type} is {@code file}, the {@code content} field holds base64-encoded file content.
 * Use {@link #getDecodedContent()} to obtain the raw text.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubFileContent {

	/** File or directory name. */
	@JsonProperty("name")
	private String name;

	/** Full path within the repository. */
	@JsonProperty("path")
	private String path;

	/** SHA of the file blob — required when updating an existing file. */
	@JsonProperty("sha")
	private String sha;

	/** File size in bytes. */
	@JsonProperty("size")
	private long size;

	/** Entry type: {@code file} or {@code dir}. */
	@JsonProperty("type")
	private String type;

	/**
	 * Base64-encoded file content. Only present for single-file responses, not directory
	 * listings or search results.
	 */
	@JsonProperty("content")
	private String content;

	/** URL to download the raw file content. */
	@JsonProperty("download_url")
	private String downloadUrl;

	/** URL to the GitHub API endpoint for this file. */
	@JsonProperty("url")
	private String url;

	/** URL to view the file on GitHub. */
	@JsonProperty("html_url")
	private String htmlUrl;

	public GitHubFileContent() {
	}

	public GitHubFileContent(String name, String path, String sha, long size, String type,
			String content, String downloadUrl) {
		this.name = name;
		this.path = path;
		this.sha = sha;
		this.size = size;
		this.type = type;
		this.content = content;
		this.downloadUrl = downloadUrl;
	}

	/**
	 * Decodes the base64-encoded {@code content} field to a UTF-8 string.
	 *
	 * <p>
	 * GitHub API includes newlines in the base64 payload; these are stripped before decoding.
	 *
	 * @return decoded file content as a string, or {@code null} if content is absent
	 */
	public String getDecodedContent() {
		if (content == null || content.isBlank()) {
			return null;
		}
		String cleaned = content.replaceAll("\\s", "");
		byte[] bytes = Base64.getDecoder().decode(cleaned);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getSha() {
		return sha;
	}

	public void setSha(String sha) {
		this.sha = sha;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getDownloadUrl() {
		return downloadUrl;
	}

	public void setDownloadUrl(String downloadUrl) {
		this.downloadUrl = downloadUrl;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getHtmlUrl() {
		return htmlUrl;
	}

	public void setHtmlUrl(String htmlUrl) {
		this.htmlUrl = htmlUrl;
	}

}
