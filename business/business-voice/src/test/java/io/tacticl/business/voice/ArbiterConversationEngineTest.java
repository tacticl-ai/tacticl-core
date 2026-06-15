package io.tacticl.business.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.strategiz.social.client.github.config.GitHubConfig;
import io.tacticl.business.profile.service.UserRepoService;
import io.tacticl.client.arbiter.conversation.ConverseTurnInput;
import io.tacticl.client.arbiter.conversation.ConversationServiceClient;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.repository.PipelineRunRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Verifies the conversational engine grounds the persona on the user's in-flight
 * pipelines — so it can answer "how's the build going" without a tool call.
 */
class ArbiterConversationEngineTest {

    private ConversationServiceClient client;
    private PipelineRunRepository pipelineRunRepository;
    private ArbiterConversationEngine engine;

    private static final String USER = "user-1";

    @BeforeEach
    void setUp() {
        client = mock(ConversationServiceClient.class);
        pipelineRunRepository = mock(PipelineRunRepository.class);
        GitHubConfig gitHubConfig = mock(GitHubConfig.class);
        when(gitHubConfig.getAppToken()).thenReturn("gh-tok");
        UserRepoService userRepoService = mock(UserRepoService.class);
        when(userRepoService.recentRepos(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        engine = new ArbiterConversationEngine(
            client, new VoiceProperties(), gitHubConfig, userRepoService, pipelineRunRepository);
    }

    private ConversationContext ctx() {
        return ctx(true);
    }

    private ConversationContext ctx(boolean canDispatch) {
        return new ConversationContext("tacticl", USER, "sess-1", "turn-1", "how's it going?",
            List.of(), canDispatch);
    }

    private ConverseTurnInput capturedInput() {
        ArgumentCaptor<ConverseTurnInput> captor = ArgumentCaptor.forClass(ConverseTurnInput.class);
        org.mockito.Mockito.verify(client).converseTurn(captor.capture(), any());
        return captor.getValue();
    }

    @Test
    void converse_groundsPersonaOnInFlightPipeline_withCurrentRole() {
        PipelineRun run = PipelineRun.create(USER, "spark-1", "build a /health endpoint",
            "https://github.com/o/r.git", "SMALL_FEATURE", List.of(), 5.0);
        run.markRunning();
        run.markRoleStarted("BUILD", "implementer");
        when(pipelineRunRepository.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(run));

        engine.converse(ctx(), mock(ConversationSink.class));

        var pipelines = capturedInput().pipelines();
        assertThat(pipelines).hasSize(1);
        assertThat(pipelines.get(0).name()).isEqualTo("SMALL_FEATURE");
        assertThat(pipelines.get(0).status()).isEqualTo("RUNNING");
        assertThat(pipelines.get(0).currentRole()).isEqualTo("implementer");
        assertThat(pipelines.get(0).blockedCheckpointId()).isEmpty();
    }

    @Test
    void converse_blockedRun_surfacesBlockedStatusAndCheckpoint() {
        PipelineRun run = PipelineRun.create(USER, "spark-2", "build x",
            "https://github.com/o/r.git", "FULL_PDLC", List.of(), 5.0);
        run.markRunning();
        run.setBlockedCheckpointId("cp-77");
        when(pipelineRunRepository.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(run));

        engine.converse(ctx(), mock(ConversationSink.class));

        var pipelines = capturedInput().pipelines();
        assertThat(pipelines).hasSize(1);
        assertThat(pipelines.get(0).status()).isEqualTo("BLOCKED");
        assertThat(pipelines.get(0).blockedCheckpointId()).isEqualTo("cp-77");
    }

    @Test
    void converse_excludesTerminalRuns() {
        PipelineRun running = PipelineRun.create(USER, "s-1", "a", "r", "SMALL_FEATURE", List.of(), 5.0);
        running.markRunning();
        PipelineRun done = PipelineRun.create(USER, "s-2", "b", "r", "SMALL_FEATURE", List.of(), 5.0);
        done.markCompleted();
        PipelineRun failed = PipelineRun.create(USER, "s-3", "c", "r", "SMALL_FEATURE", List.of(), 5.0);
        failed.markFailed("boom");
        when(pipelineRunRepository.findByUserIdOrderByCreatedAtDesc(USER))
            .thenReturn(List.of(done, running, failed));

        engine.converse(ctx(), mock(ConversationSink.class));

        var pipelines = capturedInput().pipelines();
        assertThat(pipelines).hasSize(1);
        assertThat(pipelines.get(0).pipelineRunId()).isEqualTo(running.getId());
    }

    @Test
    void converse_repositoryFailure_doesNotBreakTurn() {
        when(pipelineRunRepository.findByUserIdOrderByCreatedAtDesc(USER))
            .thenThrow(new RuntimeException("mongo down"));

        engine.converse(ctx(), mock(ConversationSink.class));

        assertThat(capturedInput().pipelines()).isEmpty();
    }

    @Test
    void converse_threadsCanDispatchTrueToArbiter() {
        when(pipelineRunRepository.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of());

        engine.converse(ctx(true), mock(ConversationSink.class));

        assertThat(capturedInput().canDispatch()).isTrue();
    }

    @Test
    void converse_threadsCanDispatchFalseToArbiter() {
        when(pipelineRunRepository.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of());

        engine.converse(ctx(false), mock(ConversationSink.class));

        // Fail-closed: a non-admin caller's turn carries canDispatch=false so the arbiter's
        // alignment gate never authorizes a build from a "yes" it shouldn't trust.
        assertThat(capturedInput().canDispatch()).isFalse();
    }
}
