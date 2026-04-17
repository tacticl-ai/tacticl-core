package io.tacticl.business.pipeline.service;

import io.tacticl.client.arbiter.ArbiterPipelineService;
import io.tacticl.data.pipeline.entity.*;
import io.tacticl.data.pipeline.repository.*;
import io.tacticl.data.sparks.repository.SparkRepository;
import io.tacticl.business.pipeline.dto.PipelineCallbackEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdlcV2ServiceCallbackTest {

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

    private PipelineRun pendingRun(String id) {
        PipelineRun run = PipelineRun.create("user-1", "spark-1", "do stuff",
                                             "github.com/u/repo", "FULL_PDLC", List.of(), 100.0);
        run.setId(id);
        run.setArbiterPipelineId("arb-1");
        return run;
    }

    @Test
    void handleCallbackEvent_alwaysPersistsEvent() {
        when(pipelineRunRepository.findById("run-1")).thenReturn(Optional.empty());
        service.handleCallbackEvent(new PipelineCallbackEvent("run-1","ROLE_COMPLETED","PM","PRODUCT","{\"costUsd\":2.1}"));
        verify(pipelineEventRepository).save(any(PipelineEvent.class));
    }

    @Test
    void handleCallbackEvent_pipelineStarted_marksRunning() {
        PipelineRun run = pendingRun("run-1");
        when(pipelineRunRepository.findById("run-1")).thenReturn(Optional.of(run));
        service.handleCallbackEvent(new PipelineCallbackEvent("run-1","PIPELINE_STARTED",null,null,"{}"));
        assertThat(run.getStatus()).isEqualTo(PipelineStatus.RUNNING);
        verify(pipelineRunRepository).save(run);
    }

    @Test
    void handleCallbackEvent_roleStarted_updatesPhaseAndRole() {
        PipelineRun run = pendingRun("run-1");
        run.markRunning();
        when(pipelineRunRepository.findById("run-1")).thenReturn(Optional.of(run));
        service.handleCallbackEvent(new PipelineCallbackEvent("run-1","ROLE_STARTED","PM","PRODUCT","{}"));
        assertThat(run.getPhases().get("PRODUCT").getRoles().get("PM").getStatus()).isEqualTo("RUNNING");
        verify(pipelineRunRepository).save(run);
    }

    @Test
    void handleCallbackEvent_roleCompleted_updatesCostAndRole() {
        PipelineRun run = pendingRun("run-1");
        run.markRunning();
        run.markRoleStarted("PRODUCT", "PM");
        when(pipelineRunRepository.findById("run-1")).thenReturn(Optional.of(run));
        service.handleCallbackEvent(new PipelineCallbackEvent("run-1","ROLE_COMPLETED","PM","PRODUCT","{\"costUsd\":2.1}"));
        assertThat(run.getTotalCostUsd()).isEqualTo(2.1);
        assertThat(run.getPhases().get("PRODUCT").getRoles().get("PM").getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void handleCallbackEvent_checkpointRequested_createsCheckpointAndPausesRun() {
        PipelineRun run = pendingRun("run-1");
        run.markRunning();
        when(pipelineRunRepository.findById("run-1")).thenReturn(Optional.of(run));
        service.handleCallbackEvent(new PipelineCallbackEvent("run-1","CHECKPOINT_REQUESTED",null,"PRODUCT",
            "{\"type\":\"PHASE_COMPLETE\",\"artifactPaths\":{\"tier1\":\"prd.md\"}}"));
        ArgumentCaptor<PipelineCheckpoint> cap = ArgumentCaptor.forClass(PipelineCheckpoint.class);
        verify(pipelineCheckpointRepository).save(cap.capture());
        assertThat(cap.getValue().getPhase()).isEqualTo("PRODUCT");
        assertThat(cap.getValue().getType()).isEqualTo("PHASE_COMPLETE");
        assertThat(run.getStatus()).isEqualTo(PipelineStatus.PAUSED_AT_CHECKPOINT);
    }

    @Test
    void handleCallbackEvent_pipelineCompleted_marksCompletedAndClosesSSE() {
        PipelineRun run = pendingRun("run-1");
        run.markRunning();
        when(pipelineRunRepository.findById("run-1")).thenReturn(Optional.of(run));
        service.handleCallbackEvent(new PipelineCallbackEvent("run-1","PIPELINE_COMPLETED",null,null,"{}"));
        assertThat(run.getStatus()).isEqualTo(PipelineStatus.COMPLETED);
        verify(pipelineEventEmitter).completeAll("run-1");
    }

    @Test
    void handleCallbackEvent_pipelineFailed_marksFailedWithReason() {
        PipelineRun run = pendingRun("run-1");
        run.markRunning();
        when(pipelineRunRepository.findById("run-1")).thenReturn(Optional.of(run));
        service.handleCallbackEvent(new PipelineCallbackEvent("run-1","PIPELINE_FAILED",null,null,"{\"reason\":\"max rework exceeded\"}"));
        assertThat(run.getStatus()).isEqualTo(PipelineStatus.FAILED);
        assertThat(run.getFailureReason()).isEqualTo("max rework exceeded");
        verify(pipelineEventEmitter).completeAll("run-1");
    }

    @Test
    void handleCallbackEvent_unknownRunId_doesNotThrow() {
        when(pipelineRunRepository.findById("unknown")).thenReturn(Optional.empty());
        service.handleCallbackEvent(new PipelineCallbackEvent("unknown","ROLE_COMPLETED","PM","PRODUCT","{}"));
        verify(pipelineEventRepository).save(any());
        verify(pipelineRunRepository, never()).save(any());
    }

    @Test
    void handleCallbackEvent_alwaysEmitsToSse() {
        PipelineRun run = pendingRun("run-1");
        when(pipelineRunRepository.findById("run-1")).thenReturn(Optional.of(run));
        service.handleCallbackEvent(new PipelineCallbackEvent("run-1","ROLE_STARTED","PM","PRODUCT","{}"));
        verify(pipelineEventEmitter).emit("run-1","ROLE_STARTED","{}");
    }
}
