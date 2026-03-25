package io.strategiz.social.data.entity;

/** Execution status of a single PDLC role within a pipeline run. */
public enum RoleStatus {

	PENDING, EXECUTING, COMPLETED, REJECTED, REWORKING, FAILED, ESCALATED, SKIPPED, AWAITING_APPROVAL

}
