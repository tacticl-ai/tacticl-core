package io.strategiz.social.business.agent.pipeline;

import io.strategiz.social.data.entity.PdlcRole;
import io.strategiz.social.data.entity.PipelineRun;

/**
 * Interface for executing a single PDLC role within a pipeline run.
 *
 * <p>The orchestrator delegates actual role execution to an implementation of this interface.
 * Wave 4 will provide the real implementation that invokes the AI engine for each role.
 * A no-op stub is registered by default until then.</p>
 */
public interface PdlcRoleExecutor {

	/**
	 * Execute a specific PDLC role within the context of a pipeline run.
	 *
	 * @param run          the pipeline run context
	 * @param role         the role to execute
	 * @param childSparkId the ID of the child spark created for this role
	 * @return the result of executing this role
	 */
	RoleExecutionResult execute(PipelineRun run, PdlcRole role, String childSparkId);

	/**
	 * Result of a single role execution, containing metrics and output references.
	 */
	record RoleExecutionResult(
			long tokens,
			java.math.BigDecimal cost,
			long durationMs,
			String model,
			String artifactId,
			boolean rejected,
			String rejectionReason,
			PdlcRole rejectionTarget
	) {

		/** Convenience constructor for a successful (non-rejecting) result. */
		public static RoleExecutionResult success(long tokens, java.math.BigDecimal cost, long durationMs,
				String model, String artifactId) {
			return new RoleExecutionResult(tokens, cost, durationMs, model, artifactId, false, null, null);
		}

		/** Convenience constructor for a rejection result (e.g., from a reviewer role). */
		public static RoleExecutionResult rejection(long tokens, java.math.BigDecimal cost, long durationMs,
				String model, PdlcRole target, String reason) {
			return new RoleExecutionResult(tokens, cost, durationMs, model, null, true, reason, target);
		}
	}

}
