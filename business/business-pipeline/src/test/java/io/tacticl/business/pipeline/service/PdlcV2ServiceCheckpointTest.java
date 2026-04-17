package io.tacticl.business.pipeline.service;

import io.tacticl.client.arbiter.ArbiterPipelineService;
import io.tacticl.data.pipeline.entity.*;
import io.tacticl.data.pipeline.repository.*;
import io.tacticl.data.sparks.repository.SparkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdlcV2ServiceCheckpointTest {

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
            pipelineCheckpointRepository, sparkRepository,
            arbiterPipelineService, pipelineEventEmitter,
            "https://callback.url"
        );
    }

    private PipelineRun pausedRun() {
        PipelineRun run = PipelineRun.create("user-1", "spark-1", "req",
                                             "repo", "FULL_PDLC", List.of(), 100.0);
        run.setId("run-1");
        run.setArbiterPipelineId("arb-1");
        run.markRunning();
        run.pauseAtCheckpoint("cp-1", "PRODUCT");
        return run;
    }

    private PipelineCheckpoint pendingCheckpoint() {
        PipelineCheckpoint cp = PipelineCheckpoint.create(
            "run-1", "spark-1", "PRODUCT", "PHASE_COMPLETE", Map.of());
        cp.setId("cp-1");
        return cp;
    }

    @Test
    void resolveCheckpoint_approved_callsArbiterAndResumesRun() {
        PipelineRun run = pausedRun();
        PipelineCheckpoint cp = pendingCheckpoint();
        when(pipelineRunRepository.findBySparkIdAndUserId("spark-1", "user-1"))
            .thenReturn(Optional.of(run));
        when(pipelineCheckpointRepository.findByIdAndPipelineRunId("cp-1", "run-1"))
            .thenReturn(Optional.of(cp));

        service.resolveCheckpoint("user-1", "spark-1", "cp-1", CheckpointDecision.APPROVED, null);

        assertThat(cp.getStatus()).isEqualTo("RESOLVED");
        assertThat(cp.getDecision()).isEqualTo("APPROVED");
        verify(pipelineCheckpointRepository).save(cp);
        verify(arbiterPipelineService).resolveCheckpoint("arb-1", "cp-1", "APPROVED", null);
        assertThat(run.getStatus()).isEqualTo(PipelineStatus.RUNNING);
        assertThat(run.getCurrentCheckpointId()).isNull();
        verify(pipelineRunRepository).save(run);
    }

    @Test
    void resolveCheckpoint_rework_callsArbiterWithFeedback() {
        PipelineRun run = pausedRun();
        PipelineCheckpoint cp = pendingCheckpoint();
        when(pipelineRunRepository.findBySparkIdAndUserId("spark-1", "user-1"))
            .thenReturn(Optional.of(run));
        when(pipelineCheckpointRepository.findByIdAndPipelineRunId("cp-1", "run-1"))
            .thenReturn(Optional.of(cp));

        service.resolveCheckpoint("user-1", "spark-1", "cp-1",
                                  CheckpointDecision.REWORK, "needs more edge cases");

        verify(arbiterPipelineService).resolveCheckpoint(
            "arb-1", "cp-1", "REWORK", "needs more edge cases");
    }

    @Test
    void resolveCheckpoint_noArbiterPipelineId_skipsArbiterCall() {
        PipelineRun run = pausedRun();
        run.setArbiterPipelineId(null);
        PipelineCheckpoint cp = pendingCheckpoint();
        when(pipelineRunRepository.findBySparkIdAndUserId("spark-1", "user-1"))
            .thenReturn(Optional.of(run));
        when(pipelineCheckpointRepository.findByIdAndPipelineRunId("cp-1", "run-1"))
            .thenReturn(Optional.of(cp));

        service.resolveCheckpoint("user-1", "spark-1", "cp-1", CheckpointDecision.APPROVED, null);

        verifyNoInteractions(arbiterPipelineService);
    }

    @Test
    void resolveCheckpoint_unknownSpark_throws() {
        when(pipelineRunRepository.findBySparkIdAndUserId("spark-x", "user-1"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.resolveCheckpoint("user-1", "spark-x", "cp-1", CheckpointDecision.APPROVED, null)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
