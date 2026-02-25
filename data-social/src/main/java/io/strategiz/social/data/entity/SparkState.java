package io.strategiz.social.data.entity;

/** Lifecycle states for a user spark. */
public enum SparkState {

	PENDING, SCHEDULED, ROUTING, EXECUTING, CHECKPOINT, COMPLETED, FAILED, CANCELLED

}
