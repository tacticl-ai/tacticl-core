package io.tacticl.business.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.cidadel.framework.exception.CidadelException;
import io.tacticl.business.pipeline.ingress.IngressDispatchService;
import io.tacticl.business.pipeline.ingress.IngressKind;
import io.tacticl.business.pipeline.ingress.IngressRequest;
import io.tacticl.data.pipeline.entity.CheckpointDecision;
import io.tacticl.data.pipeline.entity.PipelineRun;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for the voice turn orchestration. STT/TTS bridges and the ingress
 * front door are faked; assertions inspect the emitted control frames and the
 * {@link IngressRequest} handed to dispatch.
 */
class VoiceSessionServiceTest {

    private IngressDispatchService ingress;
    private VoiceSessionRegistry registry;
    private DeepgramSttBridge stt;
    private ElevenLabsTtsBridge tts;
    private VoiceSessionService service;

    @BeforeEach
    void setUp() {
        ingress = mock(IngressDispatchService.class);
        registry = new VoiceSessionRegistry();
        stt = mock(DeepgramSttBridge.class);
        tts = mock(ElevenLabsTtsBridge.class);

        // Chained registration calls (onPartial(..).onFinal(..)...) must return the bridge.
        when(stt.onPartial(any())).thenReturn(stt);
        when(stt.onFinal(any())).thenReturn(stt);
        when(stt.onSpeechStarted(any())).thenReturn(stt);
        when(stt.onError(any())).thenReturn(stt);
        when(tts.onAudioChunk(any())).thenReturn(tts);
        when(tts.onDone(any())).thenReturn(tts);
        when(tts.onError(any())).thenReturn(tts);

        DeepgramSttBridgeFactory sttFactory = new DeepgramSttBridgeFactory(null) {
            @Override
            public DeepgramSttBridge create() {
                return stt;
            }
        };
        ElevenLabsTtsBridgeFactory ttsFactory = new ElevenLabsTtsBridgeFactory(null) {
            @Override
            public ElevenLabsTtsBridge create(String voiceId) {
                return tts;
            }
        };
        VoiceProperties props = new VoiceProperties();
        props.setEnabled(true);
        service = new VoiceSessionService(sttFactory, ttsFactory, ingress, registry, props);
    }

    private VoiceSession open(RecordingOutbound out) {
        return service.openSession("user-1", out);
    }

    @Test
    void classify_buildVerb_returnsExplicitTrigger() {
        assertThat(service.classify("build me a login page")).isEqualTo(IngressKind.EXPLICIT_TRIGGER);
        assertThat(service.classify("Fix the flaky test")).isEqualTo(IngressKind.EXPLICIT_TRIGGER);
    }

    @Test
    void classify_question_returnsConversationTurn() {
        assertThat(service.classify("what is the status of the run")).isEqualTo(IngressKind.CONVERSATION_TURN);
        assertThat(service.classify("tell me about the pipeline")).isEqualTo(IngressKind.CONVERSATION_TURN);
    }

    @Test
    void mapDecision_protocolTokens_mapToBackendVerbs() {
        assertThat(VoiceSessionService.mapDecision("APPROVE")).isEqualTo(CheckpointDecision.APPROVED);
        assertThat(VoiceSessionService.mapDecision("CHANGES")).isEqualTo(CheckpointDecision.REWORK);
        assertThat(VoiceSessionService.mapDecision("REJECT")).isEqualTo(CheckpointDecision.CANCEL);
    }

    @Test
    void openSession_registersSessionAndWiresBridges() {
        RecordingOutbound out = new RecordingOutbound();
        VoiceSession session = open(out);

        assertThat(registry.bySessionId(session.sessionId())).contains(session);
        assertThat(registry.activeSessions()).isEqualTo(1);
        verify(stt).onFinal(any());
        verify(tts).onAudioChunk(any());
    }

    @Test
    void startTurn_opensSttAndEmitsListeningState() {
        RecordingOutbound out = new RecordingOutbound();
        VoiceSession session = open(out);

        service.startTurn(session);

        verify(stt).open();
        verify(tts).stop();
        assertThat(out.lastState()).contains("listening");
    }

    @Test
    void onFinalTranscript_explicitTrigger_dispatchesAndBindsRun() {
        RecordingOutbound out = new RecordingOutbound();
        VoiceSession session = open(out);
        PipelineRun run = PipelineRun.create("user-1", "spark-9", "build a thing",
            "repo", "FULL_PDLC", List.of(), 5.0);
        when(ingress.dispatch(any())).thenReturn(Optional.of(run));

        service.onFinalTranscript(session, "build a login page");

        ArgumentCaptor<IngressRequest> captor = ArgumentCaptor.forClass(IngressRequest.class);
        verify(ingress).dispatch(captor.capture());
        IngressRequest req = captor.getValue();
        assertThat(req.kind()).isEqualTo(IngressKind.EXPLICIT_TRIGGER);
        assertThat(req.text()).isEqualTo("build a login page");
        assertThat(req.tacticlUserId()).isEqualTo("user-1");
        assertThat(req.origin().channel().name()).isEqualTo("VOICE");
        assertThat(req.origin().externalKey()).isEqualTo(VoiceSessionService.VOICE_EXTERNAL_KEY);

        // The returned run is bound both on the session and in the registry reverse index.
        assertThat(session.activeRunId()).isEqualTo(run.getId());
        assertThat(registry.byRunId(run.getId())).contains(session);
        // A thinking state was emitted, and the submitted HUD frame carries the run id.
        assertThat(out.controls.stream().anyMatch(f -> "state".equals(f.get("type"))
            && "thinking".equals(f.get("state")))).isTrue();
        assertThat(out.framesOfType("hud").stream()
            .anyMatch(f -> run.getId().equals(f.get("runId")))).isTrue();
    }

    @Test
    void onFinalTranscript_conversationTurn_dispatchesAndReturnsToIdle() {
        RecordingOutbound out = new RecordingOutbound();
        VoiceSession session = open(out);
        when(ingress.dispatch(any())).thenReturn(Optional.empty());

        service.onFinalTranscript(session, "how is the run going");

        ArgumentCaptor<IngressRequest> captor = ArgumentCaptor.forClass(IngressRequest.class);
        verify(ingress).dispatch(captor.capture());
        assertThat(captor.getValue().kind()).isEqualTo(IngressKind.CONVERSATION_TURN);
        assertThat(session.activeRunId()).isNull();
        assertThat(out.lastState()).contains("idle");
    }

    @Test
    void handleTypedText_routesThroughDispatchAndSupersedesNarration() {
        RecordingOutbound out = new RecordingOutbound();
        VoiceSession session = open(out);
        when(ingress.dispatch(any())).thenReturn(Optional.empty());

        service.handleTypedText(session, "how is the run going");

        // Supersedes any in-flight narration, then routes identically to a spoken final.
        verify(tts).stop();
        ArgumentCaptor<IngressRequest> captor = ArgumentCaptor.forClass(IngressRequest.class);
        verify(ingress).dispatch(captor.capture());
        IngressRequest req = captor.getValue();
        assertThat(req.kind()).isEqualTo(IngressKind.CONVERSATION_TURN);
        assertThat(req.text()).isEqualTo("how is the run going");
        assertThat(req.origin().channel().name()).isEqualTo("VOICE");
        // The client owns/renders the user's typed turn — the server must NOT echo a user transcript.
        assertThat(out.framesOfType("transcript").stream()
            .noneMatch(f -> "user".equals(f.get("role")))).isTrue();
    }

    @Test
    void handleTypedText_blankOrNull_isNoOp() {
        RecordingOutbound out = new RecordingOutbound();
        VoiceSession session = open(out);

        service.handleTypedText(session, "   ");
        service.handleTypedText(session, null);

        verify(ingress, never()).dispatch(any());
    }

    @Test
    void onFinalTranscript_blank_throwsEmptyTranscript() {
        RecordingOutbound out = new RecordingOutbound();
        VoiceSession session = open(out);

        assertThatThrownBy(() -> service.onFinalTranscript(session, "   "))
            .isInstanceOf(CidadelException.class);
        verify(ingress, never()).dispatch(any());
    }

    @Test
    void onFinalTranscript_dispatchRejected_emitsErrorFrameNotCrash() {
        RecordingOutbound out = new RecordingOutbound();
        VoiceSession session = open(out);
        when(ingress.dispatch(any()))
            .thenThrow(new CidadelException(VoiceErrorDetails.STT_FAILED, "business-voice", "nope"));

        service.onFinalTranscript(session, "build a login page");

        assertThat(out.framesOfType("error")).isNotEmpty();
        assertThat(out.lastState()).contains("idle");
    }

    @Test
    void submitDecision_resolvesCheckpointAndMapsApprove() {
        RecordingOutbound out = new RecordingOutbound();
        VoiceSession session = open(out);
        session.registerCheckpoint("cp-1", "spark-9");

        service.submitDecision(session, "cp-1", "APPROVE", null);

        ArgumentCaptor<IngressRequest> captor = ArgumentCaptor.forClass(IngressRequest.class);
        verify(ingress).dispatch(captor.capture());
        IngressRequest req = captor.getValue();
        assertThat(req.kind()).isEqualTo(IngressKind.CHECKPOINT_DECISION);
        assertThat(req.decision().sparkId()).isEqualTo("spark-9");
        assertThat(req.decision().checkpointId()).isEqualTo("cp-1");
        assertThat(req.decision().decision()).isEqualTo(CheckpointDecision.APPROVED);
    }

    @Test
    void submitDecision_reject_routesAsCancelRun() {
        RecordingOutbound out = new RecordingOutbound();
        VoiceSession session = open(out);
        session.registerCheckpoint("cp-2", "spark-9");

        service.submitDecision(session, "cp-2", "REJECT", "not good");

        ArgumentCaptor<IngressRequest> captor = ArgumentCaptor.forClass(IngressRequest.class);
        verify(ingress).dispatch(captor.capture());
        assertThat(captor.getValue().kind()).isEqualTo(IngressKind.CANCEL_RUN);
        assertThat(captor.getValue().decision().decision()).isEqualTo(CheckpointDecision.CANCEL);
    }

    @Test
    void submitDecision_unknownCheckpoint_throwsUnresolvable() {
        RecordingOutbound out = new RecordingOutbound();
        VoiceSession session = open(out);

        assertThatThrownBy(() -> service.submitDecision(session, "ghost", "APPROVE", null))
            .isInstanceOf(CidadelException.class);
        verify(ingress, never()).dispatch(any());
    }

    @Test
    void bargeIn_stopsTtsAndReListens() {
        RecordingOutbound out = new RecordingOutbound();
        VoiceSession session = open(out);

        service.bargeIn(session);

        verify(tts, times(1)).stop();
        assertThat(out.lastState()).contains("listening");
    }

    @Test
    void closeSession_tearsDownBridgesAndRegistry() {
        RecordingOutbound out = new RecordingOutbound();
        VoiceSession session = open(out);

        service.closeSession(session);

        verify(stt).close();
        verify(tts).stop();
        assertThat(registry.bySessionId(session.sessionId())).isEmpty();
    }

    @Test
    void pushAudio_forwardsToStt() {
        RecordingOutbound out = new RecordingOutbound();
        VoiceSession session = open(out);
        byte[] chunk = {1, 2, 3, 4};

        service.pushAudio(session, chunk);

        verify(stt).sendAudio(chunk);
    }
}
