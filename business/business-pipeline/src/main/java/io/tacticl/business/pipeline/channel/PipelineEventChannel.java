package io.tacticl.business.pipeline.channel;

// Pluggable sink for pipeline events. Multiple implementations (SSE, Telegram, etc.) can coexist;
// PipelineEventEmitter fans each event out to every registered channel.
public interface PipelineEventChannel {
    void emit(String pipelineRunId, String eventName, Object payload);

    default void complete(String pipelineRunId) {}
}
