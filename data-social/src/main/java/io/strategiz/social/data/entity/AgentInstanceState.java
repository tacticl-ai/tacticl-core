package io.strategiz.social.data.entity;

/** Lifecycle states for an LLM agent instance working on a task. */
public enum AgentInstanceState {

	INITIALIZING, RUNNING, COMPLETED, FAILED, CANCELLED

}
