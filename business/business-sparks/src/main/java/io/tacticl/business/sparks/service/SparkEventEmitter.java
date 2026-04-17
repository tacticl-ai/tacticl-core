package io.tacticl.business.sparks.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SparkEventEmitter {

    private final ConcurrentHashMap<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String sparkId, SseEmitter emitter) {
        emitters.computeIfAbsent(sparkId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(emitter);
        emitter.onCompletion(() -> unregister(sparkId, emitter));
        emitter.onTimeout(() -> unregister(sparkId, emitter));
        emitter.onError(e -> unregister(sparkId, emitter));
        return emitter;
    }

    public void unregister(String sparkId, SseEmitter emitter) {
        emitters.computeIfPresent(sparkId, (k, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }

    public void emit(String sparkId, String eventName, Object data) {
        Set<SseEmitter> set = emitters.getOrDefault(sparkId, Collections.emptySet());
        Set<SseEmitter> failed = Collections.newSetFromMap(new ConcurrentHashMap<>());
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                failed.add(emitter);
            }
        }
        failed.forEach(e -> unregister(sparkId, e));
    }

    public void completeAll(String sparkId) {
        Set<SseEmitter> set = emitters.remove(sparkId);
        if (set != null) set.forEach(SseEmitter::complete);
    }

    /** For tests only. */
    int activeCount(String sparkId) {
        return emitters.getOrDefault(sparkId, Collections.emptySet()).size();
    }
}
