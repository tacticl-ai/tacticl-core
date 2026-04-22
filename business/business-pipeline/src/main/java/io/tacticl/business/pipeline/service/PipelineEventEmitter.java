package io.tacticl.business.pipeline.service;

import io.tacticl.business.pipeline.channel.PipelineEventChannel;
import io.tacticl.business.pipeline.channel.SseEventChannel;
import io.tacticl.business.pipeline.dto.PipelineCallbackEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

// Fan-out emitter. Dispatches every pipeline event to every registered
// PipelineEventChannel (SSE today; Telegram, audit log, etc. in future).
// The SSE channel is also exposed directly so REST controllers can still
// register SseEmitter subscribers via the historic API.
//
// Every channel invocation is isolated in a try/catch (Throwable): a single
// misbehaving channel must not halt fan-out to siblings — e.g. a Telegram
// outage cannot prevent SSE subscribers from receiving role updates.
@Service
public class PipelineEventEmitter {

    private static final Logger log = LoggerFactory.getLogger(PipelineEventEmitter.class);

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

    public void emit(PipelineCallbackEvent event) {
        for (PipelineEventChannel channel : channels) {
            try {
                channel.emit(event);
            } catch (Throwable t) {
                log.warn("Channel {} failed emitting {} for run {}: {}",
                         channel.getClass().getSimpleName(), event.eventType(),
                         event.pipelineRunId(), t.toString(), t);
            }
        }
    }

    public void completeAll(String pipelineRunId) {
        for (PipelineEventChannel channel : channels) {
            try {
                channel.complete(pipelineRunId);
            } catch (Throwable t) {
                log.warn("Channel {} failed completing run {}: {}",
                         channel.getClass().getSimpleName(), pipelineRunId, t.toString(), t);
            }
        }
    }

    // Visible for tests only.
    int activeCount(String pipelineRunId) {
        return sseChannel.activeCount(pipelineRunId);
    }
}
