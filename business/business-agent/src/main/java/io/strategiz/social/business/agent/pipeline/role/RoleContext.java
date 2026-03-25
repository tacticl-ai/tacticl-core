package io.strategiz.social.business.agent.pipeline.role;

import io.strategiz.social.business.agent.pipeline.PlaybookConfig;
import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineArtifact;
import java.util.Map;

/**
 * Immutable context passed to a {@link PdlcRoleSkill} at execution time.
 * Carries all information the skill needs to build its prompt and invoke the AI engine.
 *
 * @param pipelineRunId     the pipeline run this execution belongs to
 * @param parentSparkId     the parent spark that initiated the pipeline
 * @param childSparkId      the child spark created for this specific role execution
 * @param userId            the user who owns this pipeline
 * @param originalRequest   the user's original request text from the parent spark
 * @param classification    spark classification metadata produced by the classifier
 * @param playbook          the playbook configuration driving this pipeline
 * @param upstreamArtifacts artifacts produced by previously completed roles, keyed by role
 * @param gitContext        git repository context for code-related pipelines (may be null)
 * @param reworkFeedback    feedback from a rejecting role (null on first execution)
 * @param reworkIteration   rework iteration count (0 on first execution)
 */
public record RoleContext(
		String pipelineRunId,
		String parentSparkId,
		String childSparkId,
		String userId,
		String originalRequest,
		Map<String, Object> classification,
		PlaybookConfig playbook,
		Map<PdlcRole, PipelineArtifact> upstreamArtifacts,
		GitContext gitContext,
		String reworkFeedback,
		int reworkIteration
) {

	/** Returns {@code true} if this execution is a rework pass (iteration > 0). */
	public boolean isRework() {
		return reworkIteration > 0;
	}

}
