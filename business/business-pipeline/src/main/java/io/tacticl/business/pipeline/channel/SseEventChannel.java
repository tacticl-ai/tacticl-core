package io.tacticl.business.pipeline.channel;

import io.tacticl.business.pipeline.dto.PipelineCallbackEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Preserves the original in-process SSE fan-out so existing REST subscribers
// (PipelineController /v1/pipelines/{id}/events) continue to work after the
// channel refactor. SSE payload shape is byte-for-byte identical to the
// pre-refactor contract: name = event.eventType(), data = event.payloadJson().
@Component
public class SseEventChannel implements PipelineEventChannel {

    private static final Logger log = LoggerFactory.getLogger(SseEventChannel.class);

    private final ConcurrentHashMap<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String pipelineRunId, SseEmitter emitter) {
        emitters.computeIfAbsent(pipelineRunId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(emitter);
        emitter.onCompletion(() -> unregister(pipelineRunId, emitter));
        emitter.onTimeout(() -> unregister(pipelineRunId, emitter));
        emitter.onError(e -> unregister(pipelineRunId, emitter));
        return emitter;
    }

    public void unregister(String pipelineRunId, SseEmitter emitter) {
        emitters.computeIfPresent(pipelineRunId, (k, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }

    @Override
    public void emit(PipelineCallbackEvent event) {
        String pipelineRunId = event.pipelineRunId();
        String eventName = event.eventType();
        Object payload = event.payloadJson();
        Set<SseEmitter> set = emitters.getOrDefault(pipelineRunId, Collections.emptySet());
        Set<SseEmitter> failed = Collections.newSetFromMap(new ConcurrentHashMap<>());
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException e) {
                log.warn("Failed to emit SSE event to subscriber for run {}: {}", pipelineRunId, e.getMessage());
                failed.add(emitter);
            }
        }
        failed.forEach(e -> unregister(pipelineRunId, e));
    }

    @Override
    public void complete(String pipelineRunId) {
        Set<SseEmitter> set = emitters.remove(pipelineRunId);
        if (set != null) set.forEach(SseEmitter::complete);
    }

    // Visible for tests only.
    public int activeCount(String pipelineRunId) {
        return emitters.getOrDefault(pipelineRunId, Collections.emptySet()).size();
    }
}
