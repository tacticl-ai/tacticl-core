package io.tacticl.data.conversation.entity;

/**
 * Legacy session status enum from the pre-Temporal {@code ConversationService}
 * flow.
 *
 * @deprecated Use {@link io.tacticl.data.cloudorchestrator.entity.SessionStatus}.
 *     This enum is retained only so the in-flight business code that still
 *     imports it continues to compile during the cloud-agent-orchestrator
 *     single-cut migration (per SAD §10 / PRD §7). It will be removed once
 *     {@code ConversationService} and its callers are deleted.
 */
@Deprecated
public enum SessionStatus {
    GATHERING,
    PROPOSING,
    ACTIVE,
    COMPLETED
}
