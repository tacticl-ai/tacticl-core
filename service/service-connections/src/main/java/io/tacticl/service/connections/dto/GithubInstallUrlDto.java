package io.tacticl.service.connections.dto;

/**
 * GitHub App install URL response.
 *
 * <p>
 * Endpoint contract: {@code GET /v1/connections/github/install/url -> { url }}. {@code url} is
 * {@code null} when the App is unconfigured.
 *
 * @param url the {@code https://github.com/apps/{slug}/installations/new} URL, or {@code null}
 */
public record GithubInstallUrlDto(String url) {
}
