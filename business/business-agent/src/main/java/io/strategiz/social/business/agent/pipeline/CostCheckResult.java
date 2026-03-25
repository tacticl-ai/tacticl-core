package io.strategiz.social.business.agent.pipeline;

/**
 * Result of a pipeline cost check against the user's configured ceiling and warning threshold.
 */
public enum CostCheckResult {

	/** Accumulated cost is below the warning threshold — pipeline may continue normally. */
	OK,

	/** Accumulated cost has crossed the warning threshold but has not yet reached the ceiling. */
	WARNING,

	/** Accumulated cost has reached or exceeded the cost ceiling — pipeline should halt. */
	CEILING_REACHED

}
