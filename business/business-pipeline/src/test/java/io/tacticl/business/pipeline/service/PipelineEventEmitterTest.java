package io.tacticl.business.pipeline.service;

import io.tacticl.business.pipeline.channel.PipelineEventChannel;
import io.tacticl.business.pipeline.channel.SseEventChannel;
import io.tacticl.business.pipeline.dto.PipelineCallbackEvent;
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

    private PipelineCallbackEvent event(String runId, String type) {
        return new PipelineCallbackEvent(runId, type, "REVIEWER", "BUILD", "{}");
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
        assertThatCode(() -> emitter.emit(event("run-1", "ROLE_COMPLETED")))
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

        PipelineCallbackEvent evt = event("run-7", "ROLE_COMPLETED");
        fanOut.emit(evt);

        assertThat(a.emitCalls).containsExactly(evt);
        assertThat(b.emitCalls).containsExactly(evt);
        assertThat(c.emitCalls).containsExactly(evt);
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

    @Test
    void emit_channelThrows_laterChannelsStillReceive() {
        // Telegram outage must not halt SSE fan-out.
        ThrowingChannel first = new ThrowingChannel();
        RecordingChannel second = new RecordingChannel();
        PipelineEventEmitter fanOut = new PipelineEventEmitter(List.of(first, second), sseChannel);

        PipelineCallbackEvent evt = event("run-10", "ROLE_COMPLETED");
        assertThatCode(() -> fanOut.emit(evt)).doesNotThrowAnyException();

        assertThat(second.emitCalls).containsExactly(evt);
    }

    @Test
    void completeAll_channelThrows_laterChannelsStillReceive() {
        ThrowingChannel first = new ThrowingChannel();
        RecordingChannel second = new RecordingChannel();
        PipelineEventEmitter fanOut = new PipelineEventEmitter(List.of(first, second), sseChannel);

        assertThatCode(() -> fanOut.completeAll("run-11")).doesNotThrowAnyException();
        assertThat(second.completeCalls).containsExactly("run-11");
    }

    @Test
    void emit_channelThrowsError_isStillIsolated() {
        // Error (e.g. OutOfMemoryError from a buggy channel) must also be caught so
        // sibling channels continue. Using Throwable in the try/catch guarantees this.
        ErrorChannel first = new ErrorChannel();
        RecordingChannel second = new RecordingChannel();
        PipelineEventEmitter fanOut = new PipelineEventEmitter(List.of(first, second), sseChannel);

        PipelineCallbackEvent evt = event("run-12", "ROLE_COMPLETED");
        assertThatCode(() -> fanOut.emit(evt)).doesNotThrowAnyException();

        assertThat(second.emitCalls).containsExactly(evt);
    }

    private static final class RecordingChannel implements PipelineEventChannel {
        final List<PipelineCallbackEvent> emitCalls = new ArrayList<>();
        final List<String> completeCalls = new ArrayList<>();

        @Override
        public void emit(PipelineCallbackEvent event) {
            emitCalls.add(event);
        }

        @Override
        public void complete(String pipelineRunId) {
            completeCalls.add(pipelineRunId);
        }
    }

    private static final class ThrowingChannel implements PipelineEventChannel {
        @Override
        public void emit(PipelineCallbackEvent event) {
            throw new RuntimeException("telegram down");
        }

        @Override
        public void complete(String pipelineRunId) {
            throw new RuntimeException("telegram down");
        }
    }

    private static final class ErrorChannel implements PipelineEventChannel {
        @Override
        public void emit(PipelineCallbackEvent event) {
            throw new AssertionError("unchecked framework bug");
        }
    }
}
