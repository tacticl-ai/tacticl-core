package io.strategiz.social.business.agent.pipeline.role;

/** Outcome states for a single PDLC role execution. */
public enum RoleOutcome {

	/** The role completed successfully and produced its artifact. */
	COMPLETED,

	/** The role rejected upstream work and requires rework. */
	REJECTED,

	/** The role encountered an unrecoverable error. */
	FAILED,

	/** The role escalated to a human checkpoint. */
	ESCALATED

}
