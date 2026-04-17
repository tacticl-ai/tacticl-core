package io.tacticl.business.pipeline.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PipelineEventEmitterTest {

    PipelineEventEmitter emitter;

    @BeforeEach
    void setUp() { emitter = new PipelineEventEmitter(); }

    @Test
    void register_addsEmitterToSet() {
        SseEmitter sse = new SseEmitter();
        emitter.register("run-1", sse);
        assertThat(emitter.activeCount("run-1")).isEqualTo(1);
    }

    @Test
    void unregister_removesEmitter() {
        SseEmitter sse = new SseEmitter();
        emitter.register("run-1", sse);
        emitter.unregister("run-1", sse);
        assertThat(emitter.activeCount("run-1")).isEqualTo(0);
    }

    @Test
    void emit_toEmptySet_doesNotThrow() {
        assertThatCode(() -> emitter.emit("run-1", "ROLE_COMPLETED", "{}"))
            .doesNotThrowAnyException();
    }

    @Test
    void completeAll_removesAllEmitters() {
        emitter.register("run-1", new SseEmitter());
        emitter.register("run-1", new SseEmitter());
        emitter.completeAll("run-1");
        assertThat(emitter.activeCount("run-1")).isEqualTo(0);
    }
}
