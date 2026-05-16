package io.strategiz.social.client.github.exception;

import io.cidadel.framework.exception.ErrorDetails;
import org.springframework.http.HttpStatus;

/**
 * Error details for GitHub REST API client operations.
 *
 * <p>
 * Implements {@link ErrorDetails} for integration with the Cidadel exception framework.
 *
 * <p>
 * Usage: {@code throw new CidadelException(GitHubErrorDetails.API_ERROR, MODULE_NAME, message);}
 */
public enum GitHubErrorDetails implements ErrorDetails {

	API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "github-api-error"),

	RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "github-rate-limit-exceeded"),

	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "github-unauthorized"),

	NOT_FOUND(HttpStatus.NOT_FOUND, "github-not-found"),

	BRANCH_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "github-branch-create-failed"),

	REPO_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "github-repo-create-failed"),

	REPO_NAME_TAKEN(HttpStatus.UNPROCESSABLE_ENTITY, "github-repo-name-taken"),

	COMMIT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "github-commit-failed"),

	PR_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "github-pr-create-failed"),

	PR_REVIEW_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "github-pr-review-failed"),

	PR_MERGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "github-pr-merge-failed"),

	FILE_READ_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "github-file-read-failed"),

	SEARCH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "github-search-failed");

	private final HttpStatus httpStatus;

	private final String propertyKey;

	GitHubErrorDetails(HttpStatus httpStatus, String propertyKey) {
		this.httpStatus = httpStatus;
		this.propertyKey = propertyKey;
	}

	@Override
	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	@Override
	public String getPropertyKey() {
		return propertyKey;
	}

}
