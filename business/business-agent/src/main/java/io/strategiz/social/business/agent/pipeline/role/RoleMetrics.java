package io.strategiz.social.business.agent.pipeline.role;

import java.math.BigDecimal;

/**
 * Execution metrics captured for a single PDLC role invocation.
 *
 * @param tokens     total tokens consumed (prompt + completion)
 * @param cost       estimated cost in USD
 * @param durationMs wall-clock execution time in milliseconds
 * @param model      model identifier used for this execution
 * @param engine     AI engine identifier used for this execution
 */
public record RoleMetrics(
		long tokens,
		BigDecimal cost,
		long durationMs,
		String model,
		String engine
) {}
