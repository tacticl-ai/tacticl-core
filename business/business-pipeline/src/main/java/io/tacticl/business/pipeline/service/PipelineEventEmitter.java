package io.tacticl.business.pipeline.service;

import io.tacticl.business.pipeline.channel.PipelineEventChannel;
import io.tacticl.business.pipeline.channel.SseEventChannel;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

// Fan-out emitter. Dispatches every pipeline event to every registered
// PipelineEventChannel (SSE today; Telegram, audit log, etc. in future).
// The SSE channel is also exposed directly so REST controllers can still
// register SseEmitter subscribers via the historic API.
@Service
public class PipelineEventEmitter {

    private final List<PipelineEventChannel> channels;
    private final SseEventChannel sseChannel;

    public PipelineEventEmitter(List<PipelineEventChannel> channels, SseEventChannel sseChannel) {
        this.channels = channels;
        this.sseChannel = sseChannel;
    }

    public SseEmitter register(String pipelineRunId, SseEmitter emitter) {
        return sseChannel.register(pipelineRunId, emitter);
    }

    public void unregister(String pipelineRunId, SseEmitter emitter) {
        sseChannel.unregister(pipelineRunId, emitter);
    }

    public void emit(String pipelineRunId, String eventName, Object data) {
        for (PipelineEventChannel channel : channels) {
            channel.emit(pipelineRunId, eventName, data);
        }
    }

    public void completeAll(String pipelineRunId) {
        for (PipelineEventChannel channel : channels) {
            channel.complete(pipelineRunId);
        }
    }

    // Visible for tests only.
    int activeCount(String pipelineRunId) {
        return sseChannel.activeCount(pipelineRunId);
    }
}
