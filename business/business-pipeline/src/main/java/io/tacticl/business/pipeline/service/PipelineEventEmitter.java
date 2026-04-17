package io.tacticl.business.pipeline.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PipelineEventEmitter {

    private static final Logger log = LoggerFactory.getLogger(PipelineEventEmitter.class);

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

    public void emit(String pipelineRunId, String eventName, Object data) {
        Set<SseEmitter> set = emitters.getOrDefault(pipelineRunId, Collections.emptySet());
        Set<SseEmitter> failed = Collections.newSetFromMap(new ConcurrentHashMap<>());
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                log.warn("Failed to emit SSE event to subscriber for run {}: {}", pipelineRunId, e.getMessage());
                failed.add(emitter);
            }
        }
        failed.forEach(e -> unregister(pipelineRunId, e));
    }

    public void completeAll(String pipelineRunId) {
        Set<SseEmitter> set = emitters.remove(pipelineRunId);
        if (set != null) set.forEach(SseEmitter::complete);
    }

    /** For tests only. */
    int activeCount(String pipelineRunId) {
        return emitters.getOrDefault(pipelineRunId, Collections.emptySet()).size();
    }
}
