package io.tacticl.data.pipeline.entity;

/**
 * Pipeline checkpoint lifecycle (per SAD §9.3).
 *
 * <p>{@code PENDING} is a legacy value kept for backward compatibility with
 * records written by the pre-v2 code paths; new writes should use {@link #OPEN}.
 */
public enum CheckpointStatus {
    OPEN,
    RESOLVED,
    CANCELLED,
    /** @deprecated Use {@link #OPEN}. Kept for backward compat with pre-v2 data. */
    @Deprecated
    PENDING
}
