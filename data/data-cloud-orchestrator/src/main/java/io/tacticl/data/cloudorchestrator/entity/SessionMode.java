package io.tacticl.data.cloudorchestrator.entity;

/**
 * Conversation session input/output mode. Used by both the WebSocket protocol
 * (per SAD §5.1) and the {@code conversation_sessions} projection.
 */
public enum SessionMode {
    VOICE_ACTIVE,
    VOICE_PTT,
    TEXT_ONLY,
    MUTED
}
