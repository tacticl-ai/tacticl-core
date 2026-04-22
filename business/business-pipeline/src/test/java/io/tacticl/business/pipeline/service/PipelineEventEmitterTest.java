package io.tacticl.business.pipeline.service;

import io.tacticl.business.pipeline.channel.PipelineEventChannel;
import io.tacticl.business.pipeline.channel.SseEventChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PipelineEventEmitterTest {

    PipelineEventEmitter emitter;
    SseEventChannel sseChannel;

    @BeforeEach
    void setUp() {
        sseChannel = new SseEventChannel();
        emitter = new PipelineEventEmitter(List.of(sseChannel), sseChannel);
    }

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

    @Test
    void emit_fansOutToEveryRegisteredChannel() {
        RecordingChannel a = new RecordingChannel();
        RecordingChannel b = new RecordingChannel();
        RecordingChannel c = new RecordingChannel();
        PipelineEventEmitter fanOut = new PipelineEventEmitter(List.of(a, b, c), sseChannel);

        fanOut.emit("run-7", "ROLE_COMPLETED", "{\"role\":\"REVIEWER\"}");

        assertThat(a.emitCalls).hasSize(1);
        assertThat(b.emitCalls).hasSize(1);
        assertThat(c.emitCalls).hasSize(1);
        assertThat(a.emitCalls.get(0)).isEqualTo(new EmitCall("run-7", "ROLE_COMPLETED", "{\"role\":\"REVIEWER\"}"));
        assertThat(b.emitCalls.get(0)).isEqualTo(new EmitCall("run-7", "ROLE_COMPLETED", "{\"role\":\"REVIEWER\"}"));
        assertThat(c.emitCalls.get(0)).isEqualTo(new EmitCall("run-7", "ROLE_COMPLETED", "{\"role\":\"REVIEWER\"}"));
    }

    @Test
    void completeAll_propagatesToEveryChannel() {
        RecordingChannel a = new RecordingChannel();
        RecordingChannel b = new RecordingChannel();
        PipelineEventEmitter fanOut = new PipelineEventEmitter(List.of(a, b), sseChannel);

        fanOut.completeAll("run-9");

        assertThat(a.completeCalls).containsExactly("run-9");
        assertThat(b.completeCalls).containsExactly("run-9");
    }

    private record EmitCall(String runId, String eventName, Object payload) {}

    private static final class RecordingChannel implements PipelineEventChannel {
        final List<EmitCall> emitCalls = new ArrayList<>();
        final List<String> completeCalls = new ArrayList<>();

        @Override
        public void emit(String pipelineRunId, String eventName, Object payload) {
            emitCalls.add(new EmitCall(pipelineRunId, eventName, payload));
        }

        @Override
        public void complete(String pipelineRunId) {
            completeCalls.add(pipelineRunId);
        }
    }
}
