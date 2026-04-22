package io.tacticl.business.pipeline.service;

import io.tacticl.client.arbiter.ArbiterPipelineService;
import io.tacticl.client.arbiter.dto.SubmitPipelineRequest;
import io.tacticl.client.arbiter.dto.SubmitPipelineResponse;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.entity.PipelineStatus;
import io.tacticl.data.pipeline.repository.PipelineRunRepository;
import io.tacticl.data.pipeline.repository.PipelineEventRepository;
import io.tacticl.data.pipeline.repository.PipelineCheckpointRepository;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.repository.SparkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdlcV2ServiceTest {

    @Mock PipelineRunRepository pipelineRunRepository;
    @Mock PipelineEventRepository pipelineEventRepository;
    @Mock PipelineCheckpointRepository pipelineCheckpointRepository;
    @Mock SparkRepository sparkRepository;
    @Mock ArbiterPipelineService arbiterPipelineService;
    @Mock PipelineEventEmitter pipelineEventEmitter;

    PdlcV2Service service;

    @BeforeEach
    void setUp() {
        service = new PdlcV2Service(
            pipelineRunRepository, pipelineEventRepository,
            pipelineCheckpointRepository, sparkRepository, arbiterPipelineService,
            pipelineEventEmitter,
            new RoleIdentityLoader(),
            new PlaybookSpecResolver(),
            "https://api.tacticl.ai/v1/internal/pipeline/callback"
        );
    }

    @Test
    void submitPipeline_createsPipelineRun_andCallsArbiter() {
        when(arbiterPipelineService.submitPipeline(any())).thenReturn(
            new SubmitPipelineResponse("run-1", "arbiter-xyz", "PENDING")
        );
        when(pipelineRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Spark mockSpark = Spark.create("user-1", "Add auth flow");
        when(sparkRepository.findByIdAndUserId("spark-1", "user-1")).thenReturn(Optional.of(mockSpark));
        when(sparkRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PipelineRun run = service.submitPipeline(
            "user-1", "spark-1", "Add auth flow",
            "github.com/user/repo", "FULL_PDLC", List.of(), "gh-token", 50.0
        );

        assertThat(run.getStatus()).isEqualTo(PipelineStatus.PENDING);
        assertThat(run.getSparkId()).isEqualTo("spark-1");
        assertThat(run.getPlaybook()).isEqualTo("FULL_PDLC");
        ArgumentCaptor<SubmitPipelineRequest> captor = ArgumentCaptor.forClass(SubmitPipelineRequest.class);
        verify(arbiterPipelineService).submitPipeline(captor.capture());
        SubmitPipelineRequest captured = captor.getValue();
        assertThat(captured.roleIdentities()).containsKeys("implementer", "reviewer", "pm");
        assertThat(captured.playbookConfigJson()).contains("FULL_PDLC");
        assertThat(captured.knowledgeNamespace()).isEqualTo("tacticl-user-1");
        verify(sparkRepository).save(any());
    }

    @Test
    void submitPipeline_sparkNotInMongo_succeedsWithoutSparkUpdate() {
        // Spark absent from MongoDB (e.g. created via legacy Firestore path) — pipeline still submits
        when(sparkRepository.findByIdAndUserId("bad-spark", "user-1")).thenReturn(Optional.empty());
        when(arbiterPipelineService.submitPipeline(any())).thenReturn(
            new SubmitPipelineResponse("run-1", "arbiter-xyz", "PENDING")
        );
        when(pipelineRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PipelineRun run = service.submitPipeline(
            "user-1", "bad-spark", "req", "url", "BUG_FIX", List.of(), "token", 10.0
        );

        assertThat(run.getSparkId()).isEqualTo("bad-spark");
        verify(sparkRepository, never()).save(any());
    }

    @Test
    void getStatus_returnsRunForUserAndSpark() {
        PipelineRun run = PipelineRun.create("user-1", "spark-1", "req", "url", "BUG_FIX", List.of(), 10.0);
        when(pipelineRunRepository.findFirstBySparkIdAndUserIdOrderByCreatedAtDesc("spark-1", "user-1")).thenReturn(Optional.of(run));

        Optional<PipelineRun> result = service.getStatus("user-1", "spark-1");

        assertThat(result).isPresent();
        assertThat(result.get().getSparkId()).isEqualTo("spark-1");
    }

    @Test
    void getStatus_notFound_returnsEmpty() {
        when(pipelineRunRepository.findFirstBySparkIdAndUserIdOrderByCreatedAtDesc("spark-1", "user-1")).thenReturn(Optional.empty());
        assertThat(service.getStatus("user-1", "spark-1")).isEmpty();
    }

    @Test
    void cancelPipeline_marksRunCancelled_callsArbiterWhenIdKnown() {
        PipelineRun run = PipelineRun.create("user-1", "spark-1", "req", "url", "BUG_FIX", List.of(), 10.0);
        run.setArbiterPipelineId("arbiter-abc");
        when(pipelineRunRepository.findFirstBySparkIdAndUserIdOrderByCreatedAtDesc("spark-1", "user-1")).thenReturn(Optional.of(run));
        when(pipelineRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        doNothing().when(arbiterPipelineService).cancelPipeline(any());

        service.cancelPipeline("user-1", "spark-1");

        assertThat(run.getStatus()).isEqualTo(io.tacticl.data.pipeline.entity.PipelineStatus.CANCELLED);
        verify(pipelineRunRepository).save(run);
        verify(arbiterPipelineService).cancelPipeline("arbiter-abc");
    }

    @Test
    void cancelPipeline_skipsArbiterWhenNoArbiterPipelineId() {
        PipelineRun run = PipelineRun.create("user-1", "spark-1", "req", "url", "BUG_FIX", List.of(), 10.0);
        // arbiterPipelineId is null (e.g. stub was used, no real arbiter)
        when(pipelineRunRepository.findFirstBySparkIdAndUserIdOrderByCreatedAtDesc("spark-1", "user-1")).thenReturn(Optional.of(run));
        when(pipelineRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.cancelPipeline("user-1", "spark-1");

        assertThat(run.getStatus()).isEqualTo(io.tacticl.data.pipeline.entity.PipelineStatus.CANCELLED);
        verifyNoInteractions(arbiterPipelineService);
    }

    @Test
    void resolveCheckpoint_sparkNotFound_throwsIllegalArgument() {
        when(pipelineRunRepository.findFirstBySparkIdAndUserIdOrderByCreatedAtDesc("bad-spark", "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveCheckpoint(
            "user-1", "bad-spark", "cp-1",
            io.tacticl.data.pipeline.entity.CheckpointDecision.APPROVED, null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("bad-spark");
    }
}
