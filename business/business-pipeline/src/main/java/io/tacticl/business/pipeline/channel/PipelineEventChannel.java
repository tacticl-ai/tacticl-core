package io.tacticl.business.pipeline.channel;

import io.tacticl.business.pipeline.dto.PipelineCallbackEvent;

// Pluggable sink for pipeline events. Multiple implementations (SSE, Telegram, etc.) can coexist;
// PipelineEventEmitter fans each event out to every registered channel.
//
// Channels receive the full PipelineCallbackEvent so they can access role/phase/checkpointId
// as first-class fields — downstream formatters (e.g. Telegram checkpoint buttons) rely on
// these, and only flattening payloadJson to string would drop that information.
public interface PipelineEventChannel {
    void emit(PipelineCallbackEvent event);

    default void complete(String pipelineRunId) {}
}
