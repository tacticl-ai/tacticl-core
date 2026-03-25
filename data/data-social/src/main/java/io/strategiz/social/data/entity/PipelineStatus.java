package io.strategiz.social.data.entity;

/** Lifecycle states for a PDLC pipeline run. */
public enum PipelineStatus {

	CREATED, CLASSIFYING, AWAITING_CONFIRMATION, EXECUTING, CHECKPOINT, COMPLETED, FAILED, CANCELLED

}
