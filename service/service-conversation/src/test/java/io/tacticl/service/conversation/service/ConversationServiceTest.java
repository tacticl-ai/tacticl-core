package io.tacticl.service.conversation.service;

import io.cidadel.client.anthropic.AnthropicDirectClient;
import io.cidadel.client.base.llm.model.LlmResponse;
import io.tacticl.business.pipeline.router.PdlcRouter;
import io.tacticl.business.sparks.service.SparkClassifierService;
import io.tacticl.business.sparks.service.SparkService;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.data.conversation.entity.SessionStatus;
import io.tacticl.data.conversation.repository.ConversationSessionRepository;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.repository.PipelineRunRepository;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.entity.SparkRoute;
import io.tacticl.data.sparks.entity.SparkType;
import io.tacticl.service.conversation.dto.MessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock ConversationSessionRepository sessionRepository;
    @Mock AnthropicDirectClient anthropicClient;
    @Mock SparkService sparkService;
    @Mock SparkClassifierService sparkClassifierService;
    @Mock PdlcRouter pdlcRouter;
    @Mock PipelineRunRepository pipelineRunRepository;

    ConversationService service;

    @BeforeEach
    void setUp() {
        service = new ConversationService(
            sessionRepository, anthropicClient, sparkService,
            sparkClassifierService, pdlcRouter, pipelineRunRepository);
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
    void sendMessage_plainResponse_staysGathering() {
        ConversationSession session = ConversationSession.create("user-1", "build me a todo app");
        when(sessionRepository.findByIdAndUserId("sess-1", "user-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LlmResponse llmResponse = mockLlmResponse("What framework do you prefer?");
        when(anthropicClient.generateContent(anyString(), anyList(), anyString())).thenReturn(llmResponse);

        MessageResponse response = service.sendMessage("sess-1", "user-1", "hi");

        assertThat(response.getContent()).isEqualTo("What framework do you prefer?");
        assertThat(response.getSessionStatus()).isEqualTo("GATHERING");
        assertThat(response.getSparkId()).isNull();
    }

    @Test
    void sendMessage_withProposeMarker_transitionsToProposing() {
        ConversationSession session = ConversationSession.create("user-1", "build me a todo app");
        when(sessionRepository.findByIdAndUserId("sess-1", "user-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sparkClassifierService.classify(anyString())).thenReturn(SparkType.CODE);

        LlmResponse llmResponse = mockLlmResponse("Here's my plan:\n- React frontend\n- Node backend\nReady to start?\n<<<PROPOSE>>>");
        when(anthropicClient.generateContent(anyString(), anyList(), anyString())).thenReturn(llmResponse);

        MessageResponse response = service.sendMessage("sess-1", "user-1", "React and Node");

        assertThat(response.getContent()).doesNotContain("<<<PROPOSE>>>");
        assertThat(response.getSessionStatus()).isEqualTo("PROPOSING");
    }

    @Test
    void sendMessage_withStartMarker_createsSparkAndPipeline() {
        ConversationSession session = ConversationSession.create("user-1", "build me a todo app");
        session.markProposing("CODE", "Build a React todo app with Node backend");
        when(sessionRepository.findByIdAndUserId("sess-1", "user-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Spark spark = Spark.create("user-1", "build me a todo app");
        when(sparkService.create(anyString(), anyString())).thenReturn(spark);
        when(sparkService.classify(anyString(), anyString(), any())).thenReturn(spark);
        when(sparkService.markExecuting(anyString(), anyString(), any(), any())).thenReturn(spark);

        PipelineRun run = PipelineRun.create("user-1", spark.getId(), "build me a todo app", null, "FULL_PDLC", java.util.List.of(), 50.0);
        when(pdlcRouter.route(anyString(), anyString(), anyString(), any(), any(), any(), any(), anyDouble()))
            .thenReturn(Optional.of(run));

        LlmResponse llmResponse = mockLlmResponse("Great, starting now!\n<<<START>>>");
        when(anthropicClient.generateContent(anyString(), anyList(), anyString())).thenReturn(llmResponse);

        MessageResponse response = service.sendMessage("sess-1", "user-1", "yes go ahead");

        assertThat(response.getContent()).doesNotContain("<<<START>>>");
        assertThat(response.getSessionStatus()).isEqualTo("ACTIVE");
        assertThat(response.getSparkId()).isNotNull();
        assertThat(response.getPipelineRunId()).isNotNull();
        verify(sparkService).create(anyString(), anyString());
    }

    @Test
    void startImplementationPassesRepoUrlAndHighCeilingToPdlcRouter() {
        ConversationSession session = ConversationSession.createForTelegramGroup(
            "user-1", "proj-1", "build X");
        session.markProposing("CODE", "Plan summary");
        session.setRepoUrl("https://github.com/owner/repo");
        when(sessionRepository.findByIdAndUserId("sid", "user-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LlmResponse llm = mockLlmResponse("Got it, starting now. <<<START>>>");
        when(anthropicClient.generateContent(eq("claude-sonnet-4-6"), anyList(), anyString()))
            .thenReturn(llm);

        Spark spark = mock(Spark.class);
        when(spark.getId()).thenReturn("spark-1");
        when(sparkService.create("user-1", "Plan summary")).thenReturn(spark);
        when(sparkService.classify("spark-1", "user-1", SparkType.CODE)).thenReturn(spark);

        PipelineRun run = mock(PipelineRun.class);
        when(run.getId()).thenReturn("run-1");
        when(pdlcRouter.route(eq("user-1"), eq("spark-1"), eq("Plan summary"),
                eq("https://github.com/owner/repo"), eq(SparkType.CODE),
                eq(java.util.List.of()), isNull(), eq(10_000.0))).thenReturn(Optional.of(run));

        MessageResponse resp = service.sendMessage("sid", "user-1", "go");

        assertThat(resp.getSparkId()).isEqualTo("spark-1");
        assertThat(resp.getPipelineRunId()).isEqualTo("run-1");
        verify(pdlcRouter).route(eq("user-1"), eq("spark-1"), eq("Plan summary"),
                eq("https://github.com/owner/repo"), eq(SparkType.CODE),
                eq(java.util.List.of()), isNull(), eq(10_000.0));
    }

    @Test
    void sendMessage_sessionNotFound_throwsException() {
        when(sessionRepository.findByIdAndUserId("missing", "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendMessage("missing", "user-1", "hello"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Session not found");
    }

    private LlmResponse mockLlmResponse(String content) {
        LlmResponse response = mock(LlmResponse.class);
        when(response.getContent()).thenReturn(content);
        return response;
    }
}
