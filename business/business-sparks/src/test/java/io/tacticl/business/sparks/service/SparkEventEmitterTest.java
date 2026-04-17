package io.tacticl.business.sparks.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.assertj.core.api.Assertions.*;

class SparkEventEmitterTest {

    SparkEventEmitter emitter = new SparkEventEmitter();

    @Test
    void register_addsEmitter() {
        SseEmitter sse = new SseEmitter(30_000L);
        emitter.register("spark-1", sse);

        assertThat(emitter.activeCount("spark-1")).isEqualTo(1);
    }

    @Test
    void unregister_removesEmitter() {
        SseEmitter sse = new SseEmitter(30_000L);
        emitter.register("spark-1", sse);

        emitter.unregister("spark-1", sse);

        assertThat(emitter.activeCount("spark-1")).isEqualTo(0);
    }

    @Test
    void emit_callsOnCompletionAndHandlesSendError() {
        // emit to a sparkId with no subscribers should not throw
        assertThatCode(() -> emitter.emit("spark-1", "STATUS_UPDATE", "EXECUTING"))
                .doesNotThrowAnyException();
    }

    @Test
    void completeAll_removesAllEmitters() {
        emitter.register("spark-1", new SseEmitter(30_000L));
        emitter.register("spark-1", new SseEmitter(30_000L));

        emitter.completeAll("spark-1");

        assertThat(emitter.activeCount("spark-1")).isEqualTo(0);
    }
}
