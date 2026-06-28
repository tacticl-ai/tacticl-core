package io.tacticl.business.conversation.service;

import io.strategiz.social.client.github.config.GitHubConfig;
import io.tacticl.business.conversation.dto.MessageResponse;
import io.tacticl.business.pipeline.router.PdlcRouter;
import io.tacticl.business.sparks.service.SparkClassifierService;
import io.tacticl.business.sparks.service.SparkService;
import io.tacticl.client.arbiter.conversation.ConverseEventListener;
import io.tacticl.client.arbiter.conversation.ConversationServiceClient;
import io.tacticl.data.cloudorchestrator.entity.SessionStatus;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.data.conversation.repository.ConversationSessionRepository;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.entity.SparkRoute;
import io.tacticl.data.sparks.entity.SparkType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock ConversationSessionRepository sessionRepository;
    @Mock ObjectProvider<ConversationServiceClient> conversationClient;
    @Mock ConversationServiceClient client;
    @Mock SparkService sparkService;
    @Mock SparkClassifierService sparkClassifierService;
    @Mock PdlcRouter pdlcRouter;
    @Mock GitHubConfig gitHubConfig;

    ConversationService service;

    @BeforeEach
    void setUp() {
        service = new ConversationService(
            sessionRepository, conversationClient, sparkService,
            sparkClassifierService, pdlcRouter, gitHubConfig);
    }

    /** Stub the streaming client to emit the given reply text then complete normally. */
    private void stubReply(String text) {
        when(conversationClient.getIfAvailable()).thenReturn(client);
        doAnswer(inv -> {
            ConverseEventListener l = inv.getArgument(1);
            l.onToken(text, "product-manager");
            l.onDone();
            return null;
        }).when(client).converseTurn(any(), any());
    }

    @Test
    void createSession_returnsGatheringSession() {
        ConversationSession saved = ConversationSession.create("user-1", "build me a todo app");
        when(sessionRepository.save(any())).thenReturn(saved);

        ConversationSession result = service.createSession("user-1", "build me a todo app");

        assertThat(result.getStatus()).isEqualTo(SessionStatus.GATHERING);
        verify(sessionRepository).save(any(ConversationSession.class));
    }

    @Test
    void sendMessage_plainReply_accumulatesAndEngages() {
        ConversationSession session = ConversationSession.create("user-1", "build me a todo app");
        when(sessionRepository.findByIdAndUserId("sess-1", "user-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubReply("What framework do you prefer?");

        MessageResponse response = service.sendMessage("sess-1", "user-1", "hi");

        assertThat(response.getContent()).isEqualTo("What framework do you prefer?");
        // ENGAGED collapses onto the web GATHERING union value.
        assertThat(response.getSessionStatus()).isEqualTo("GATHERING");
        assertThat(response.getSparkId()).isNull();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ENGAGED);
        verifyNoInteractions(pdlcRouter);
    }

    @Test
    void sendMessage_startPipelineSkill_dispatchesAndReportsActive() {
        ConversationSession session = ConversationSession.create("user-1", "build me a todo app");
        when(sessionRepository.findByIdAndUserId("sess-1", "user-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(conversationClient.getIfAvailable()).thenReturn(client);
        doAnswer(inv -> {
            ConverseEventListener l = inv.getArgument(1);
            l.onToken("Starting the build now.", "product-manager");
            l.onToolUse("start_pipeline",
                "{\"sparkInput\":\"Build a React todo app\",\"repoUrl\":\"https://github.com/owner/repo\"}", true);
            l.onDone();
            return null;
        }).when(client).converseTurn(any(), any());

        Spark spark = mock(Spark.class);
        when(spark.getId()).thenReturn("spark-1");
        when(sparkService.create("user-1", "Build a React todo app")).thenReturn(spark);
        when(sparkClassifierService.classify("Build a React todo app")).thenReturn(SparkType.CODE);
        when(sparkService.classify("spark-1", "user-1", SparkType.CODE)).thenReturn(spark);
        when(sparkService.markExecuting(anyString(), anyString(), any(), any())).thenReturn(spark);

        PipelineRun run = mock(PipelineRun.class);
        when(run.getId()).thenReturn("run-1");
        when(pdlcRouter.route(eq("user-1"), eq("spark-1"), eq("Build a React todo app"),
                eq("https://github.com/owner/repo"), eq(SparkType.CODE), anyList(), any(), eq(10_000.0)))
            .thenReturn(Optional.of(run));

        MessageResponse response = service.sendMessage("sess-1", "user-1", "yes go ahead");

        assertThat(response.getContent()).isEqualTo("Starting the build now.");
        assertThat(response.getSessionStatus()).isEqualTo("ACTIVE");
        assertThat(response.getSparkId()).isEqualTo("spark-1");
        assertThat(response.getPipelineRunId()).isEqualTo("run-1");
        assertThat(session.getStatus()).isEqualTo(SessionStatus.PIPELINE_ACTIVE);
        verify(sparkService).markExecuting("spark-1", "user-1", SparkRoute.CLOUD, null);
    }

    @Test
    void sendMessage_clientOffline_degradesGracefully() {
        ConversationSession session = ConversationSession.create("user-1", "build me a todo app");
        when(sessionRepository.findByIdAndUserId("sess-1", "user-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(conversationClient.getIfAvailable()).thenReturn(null);

        MessageResponse response = service.sendMessage("sess-1", "user-1", "hi");

        assertThat(response.getContent()).contains("offline");
        assertThat(response.getSessionStatus()).isEqualTo("GATHERING");
        assertThat(response.getSparkId()).isNull();
        verifyNoInteractions(pdlcRouter);
    }

    @Test
    void sendMessage_arbiterError_surfacesSafeMessage() {
        ConversationSession session = ConversationSession.create("user-1", "build me a todo app");
        when(sessionRepository.findByIdAndUserId("sess-1", "user-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(conversationClient.getIfAvailable()).thenReturn(client);
        doAnswer(inv -> {
            ConverseEventListener l = inv.getArgument(1);
            l.onError("The brain is busy — try again.");
            return null;
        }).when(client).converseTurn(any(), any());

        MessageResponse response = service.sendMessage("sess-1", "user-1", "hi");

        assertThat(response.getContent()).isEqualTo("The brain is busy — try again.");
        assertThat(response.getSessionStatus()).isEqualTo("GATHERING");
    }

    @Test
    void sendMessage_sessionNotFound_throwsException() {
        when(sessionRepository.findByIdAndUserId("missing", "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendMessage("missing", "user-1", "hello"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Session not found");
    }
}
