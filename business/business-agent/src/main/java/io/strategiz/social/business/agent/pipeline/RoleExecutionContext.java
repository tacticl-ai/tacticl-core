package io.strategiz.social.business.agent.pipeline;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineArtifact;
import java.util.Map;

/**
 * Immutable context passed to the role executor for each PDLC role invocation.
 * Carries the pipeline identifiers, upstream artifacts, and optional rework feedback
 * so the executor can build the appropriate prompt and execution environment.
 *
 * @param pipelineRunId      the pipeline run this execution belongs to
 * @param parentSparkId      the parent spark that initiated the pipeline
 * @param childSparkId       the child spark created for this specific role execution
 * @param userId             the user who owns this pipeline
 * @param originalRequest    the user's original request text from the parent spark
 * @param playbook           the playbook configuration driving this pipeline
 * @param upstreamArtifacts  artifacts produced by previously completed roles, keyed by role
 * @param reworkFeedback     feedback from a rejecting role (null on first execution)
 * @param reworkIteration    rework iteration count (0 on first execution)
 */
public record RoleExecutionContext(
		String pipelineRunId,
		String parentSparkId,
		String childSparkId,
		String userId,
		String originalRequest,
		PlaybookConfig playbook,
		Map<PdlcRole, PipelineArtifact> upstreamArtifacts,
		String reworkFeedback,
		int reworkIteration
) {

	/**
	 * Returns {@code true} if this execution is a rework pass (iteration > 0).
	 */
	public boolean isRework() {
		return reworkIteration > 0;
	}

	/**
	 * Convenience factory for initial (non-rework) execution.
	 */
	public static RoleExecutionContext initial(String pipelineRunId, String parentSparkId, String childSparkId,
			String userId, String originalRequest, PlaybookConfig playbook,
			Map<PdlcRole, PipelineArtifact> upstreamArtifacts) {
		return new RoleExecutionContext(pipelineRunId, parentSparkId, childSparkId, userId,
				originalRequest, playbook, upstreamArtifacts, null, 0);
	}

	/**
	 * Derive a rework context from this context with feedback and incremented iteration.
	 */
	public RoleExecutionContext withRework(String feedback, int iteration) {
		return new RoleExecutionContext(pipelineRunId, parentSparkId, childSparkId, userId,
				originalRequest, playbook, upstreamArtifacts, feedback, iteration);
	}

}
