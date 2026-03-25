package io.strategiz.social.business.agent.pipeline.role;

/**
 * Defines what a successful role execution must produce.
 *
 * @param description          human-readable description of what success looks like
 * @param requiredArtifactType the artifact type that must be present in the role result
 */
public record SuccessCriteria(
		String description,
		String requiredArtifactType
) {}
