package io.strategiz.social.business.agent.pipeline.role;

import io.strategiz.social.data.entity.PdlcRole;
import java.util.List;
import java.util.Map;

/**
 * Result produced by a {@link PdlcRoleSkill} after execution.
 *
 * @param outcome         the final outcome of the role execution
 * @param artifacts       artifacts produced by this role (list of content maps)
 * @param summary         human-readable summary of what the role accomplished
 * @param rejectionReason explanation of why the role rejected upstream work (null if not rejected)
 * @param reworkTarget    the upstream role that should redo its work (null if not rejected)
 * @param metrics         token, cost, and timing metrics for this execution
 */
public record RoleResult(
		RoleOutcome outcome,
		List<Map<String, Object>> artifacts,
		String summary,
		String rejectionReason,
		PdlcRole reworkTarget,
		RoleMetrics metrics
) {

	/** Convenience factory for a successful completion result. */
	public static RoleResult completed(List<Map<String, Object>> artifacts, String summary, RoleMetrics metrics) {
		return new RoleResult(RoleOutcome.COMPLETED, artifacts, summary, null, null, metrics);
	}

	/** Convenience factory for a rejection result targeting an upstream role. */
	public static RoleResult rejected(String reason, PdlcRole reworkTarget, RoleMetrics metrics) {
		return new RoleResult(RoleOutcome.REJECTED, List.of(), null, reason, reworkTarget, metrics);
	}

	/** Convenience factory for a failure result. */
	public static RoleResult failed(String reason, RoleMetrics metrics) {
		return new RoleResult(RoleOutcome.FAILED, List.of(), null, reason, null, metrics);
	}

}
