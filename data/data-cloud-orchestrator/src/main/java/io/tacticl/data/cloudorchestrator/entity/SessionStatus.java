package io.tacticl.data.cloudorchestrator.entity;

/**
 * Conversation session status — extended state machine for the cloud agent orchestrator
 * (per PRD §5.1 / SAD §9.1).
 *
 * <p>This supersedes the original {@code io.tacticl.data.conversation.entity.SessionStatus}
 * (kept as a thin alias for backward compat during the cut-over).
 */
public enum SessionStatus {
    IDLE,
    ENGAGED,
    GATHERING,
    PROPOSING,
    CONFIRMED,
    PIPELINE_ACTIVE,
    PIPELINE_BLOCKED,
    COMPLETED,
    ABANDONED,
    CANCELLED
}
