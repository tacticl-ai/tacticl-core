package io.tacticl.service.connections.dto;

/**
 * GitHub App install callback body.
 *
 * <p>
 * Endpoint contract: {@code POST /v1/connections/github/install/callback}
 * body {@code { installationId, setupAction, orgLogin? }}.
 *
 * @param installationId the GitHub App installation id returned by the install redirect
 * @param setupAction GitHub's {@code setup_action} ({@code install} / {@code update}); accepted but
 * not required to persist
 * @param orgLogin the org login the installation belongs to (optional)
 */
public record GithubInstallCallbackRequestDto(
		Long installationId,
		String setupAction,
		String orgLogin) {
}
