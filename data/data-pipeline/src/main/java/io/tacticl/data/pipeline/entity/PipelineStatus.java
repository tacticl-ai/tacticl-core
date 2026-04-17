package io.tacticl.data.pipeline.entity;

public enum PipelineStatus {
    PENDING, RUNNING, PAUSED_AT_CHECKPOINT, COMPLETED, FAILED, CANCELLED
}
