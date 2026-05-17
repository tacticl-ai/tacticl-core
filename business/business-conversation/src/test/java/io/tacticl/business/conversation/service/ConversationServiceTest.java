package io.tacticl.business.conversation.service;

import io.cidadel.client.anthropic.AnthropicDirectClient;
import io.cidadel.client.base.llm.model.LlmResponse;
import io.cidadel.framework.exception.CidadelException;
import io.strategiz.social.client.github.GitHubClient;
import io.strategiz.social.client.github.config.GitHubConfig;
import io.strategiz.social.client.github.exception.GitHubErrorDetails;
import io.strategiz.social.client.github.model.GitHubRepository;
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
import io.tacticl.business.conversation.dto.MessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
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
    @Mock GitHubClient gitHubClient;
    @Mock GitHubConfig gitHubConfig;

    ConversationService service;

    @BeforeEach
    void setUp() {
        service = new ConversationService(
            sessionRepository, anthropicClient, sparkService,
            sparkClassifierService, pdlcRouter, pipelineRunRepository,
            gitHubClient, gitHubConfig);
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
                eq(List.of()), isNull(), eq(10_000.0))).thenReturn(Optional.of(run));

        MessageResponse resp = service.sendMessage("sid", "user-1", "go");

        assertThat(resp.getSparkId()).isEqualTo("spark-1");
        assertThat(resp.getPipelineRunId()).isEqualTo("run-1");
        verify(pdlcRouter).route(eq("user-1"), eq("spark-1"), eq("Plan summary"),
                eq("https://github.com/owner/repo"), eq(SparkType.CODE),
                eq(List.of()), isNull(), eq(10_000.0));
    }

    @Test
    void sendMessage_sessionNotFound_throwsException() {
        when(sessionRepository.findByIdAndUserId("missing", "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendMessage("missing", "user-1", "hello"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Session not found");
    }

    @Test
    void createRepoMarker_invokesGitHubAndPersistsHtmlUrlOnSession() {
        ConversationSession session = ConversationSession.create("user-1", "build a pdf converter");
        when(sessionRepository.findByIdAndUserId("sess-1", "user-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LlmResponse llm = mockLlmResponse(
            "Setting up your repo now.\n"
            + "<<<CREATE_REPO:{\"name\":\"markdown-pdf\",\"owner\":\"cuztomizer\",\"private\":true}>>>");
        when(anthropicClient.generateContent(anyString(), anyList(), anyString())).thenReturn(llm);

        when(gitHubConfig.getAppToken()).thenReturn("ghp_test");
        GitHubRepository repo = new GitHubRepository(
            "markdown-pdf", "cuztomizer/markdown-pdf",
            "https://github.com/owner/repo",
            "https://github.com/owner/repo.git",
            "git@github.com:owner/repo.git",
            true, "main");
        when(gitHubClient.createRepo("markdown-pdf", "cuztomizer", true, null, "ghp_test"))
            .thenReturn(repo);

        MessageResponse response = service.sendMessage("sess-1", "user-1", "yes go ahead");

        verify(gitHubClient).createRepo("markdown-pdf", "cuztomizer", true, null, "ghp_test");
        assertThat(session.getRepoUrl()).isEqualTo("https://github.com/owner/repo");
        assertThat(response.getContent()).contains("https://github.com/owner/repo");
        assertThat(response.getContent()).doesNotContain("<<<CREATE_REPO");
        verify(sessionRepository, atLeastOnce()).save(session);
    }

    @Test
    void createRepoMarker_onGitHubFailure_appendsErrorToReply() {
        ConversationSession session = ConversationSession.create("user-1", "build a pdf converter");
        when(sessionRepository.findByIdAndUserId("sess-1", "user-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LlmResponse llm = mockLlmResponse(
            "Setting up your repo now.\n"
            + "<<<CREATE_REPO:{\"name\":\"taken-name\",\"owner\":\"cuztomizer\",\"private\":true}>>>");
        when(anthropicClient.generateContent(anyString(), anyList(), anyString())).thenReturn(llm);

        when(gitHubConfig.getAppToken()).thenReturn("ghp_test");
        when(gitHubClient.createRepo(eq("taken-name"), eq("cuztomizer"), eq(true), isNull(), eq("ghp_test")))
            .thenThrow(new CidadelException(GitHubErrorDetails.REPO_NAME_TAKEN, "client-github", "Name already taken"));

        MessageResponse response = service.sendMessage("sess-1", "user-1", "yes go ahead");

        assertThat(session.getRepoUrl()).isNull();
        assertThat(response.getContent()).doesNotContain("<<<CREATE_REPO");
        assertThat(response.getContent()).contains("Couldn't create the repo");
        assertThat(response.getContent()).contains("repo name taken");
        verify(sessionRepository, atLeastOnce()).save(session);
    }

    @Test
    void createRepoMarker_malformedJson_appendsErrorAndSkipsClient() {
        ConversationSession session = ConversationSession.create("user-1", "build something");
        when(sessionRepository.findByIdAndUserId("sess-1", "user-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LlmResponse llm = mockLlmResponse("Hi there <<<CREATE_REPO:not-json>>>");
        when(anthropicClient.generateContent(anyString(), anyList(), anyString())).thenReturn(llm);

        MessageResponse response = service.sendMessage("sess-1", "user-1", "yes go ahead");

        verify(gitHubClient, never()).createRepo(anyString(), anyString(), anyBoolean(), any(), anyString());
        assertThat(response.getContent()).doesNotContain("<<<CREATE_REPO");
        assertThat(response.getContent()).contains("couldn't parse the repo spec");
        assertThat(response.getSessionStatus()).isEqualTo("GATHERING");
    }

    @Test
    void gatheringPrompt_mentionsCurrentRepoStatus_whenRepoSet() {
        ConversationSession session = ConversationSession.create("user-1", "build me a todo app");
        session.setRepoUrl("https://github.com/foo/bar");
        when(sessionRepository.findByIdAndUserId("sess-1", "user-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LlmResponse llm = mockLlmResponse("Cool, what next?");
        when(anthropicClient.generateContent(anyString(), anyList(), anyString())).thenReturn(llm);

        service.sendMessage("sess-1", "user-1", "hi");

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(anthropicClient).generateContent(anyString(), anyList(), systemPromptCaptor.capture());
        assertThat(systemPromptCaptor.getValue()).contains("https://github.com/foo/bar");
    }

    @Test
    void gatheringPrompt_mentionsNoRepoYet_whenRepoMissing() {
        ConversationSession session = ConversationSession.create("user-1", "build me a todo app");
        when(sessionRepository.findByIdAndUserId("sess-1", "user-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LlmResponse llm = mockLlmResponse("Cool, what next?");
        when(anthropicClient.generateContent(anyString(), anyList(), anyString())).thenReturn(llm);

        service.sendMessage("sess-1", "user-1", "hi");

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(anthropicClient).generateContent(anyString(), anyList(), systemPromptCaptor.capture());
        assertThat(systemPromptCaptor.getValue()).contains("not yet created");
    }

    private LlmResponse mockLlmResponse(String content) {
        LlmResponse response = mock(LlmResponse.class);
        when(response.getContent()).thenReturn(content);
        return response;
    }
}
