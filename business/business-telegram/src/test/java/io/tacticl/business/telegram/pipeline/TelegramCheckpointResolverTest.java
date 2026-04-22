package io.tacticl.business.telegram.pipeline;

import io.tacticl.business.pipeline.service.PdlcV2Service;
import io.tacticl.data.pipeline.entity.CheckpointDecision;
import io.tacticl.data.pipeline.entity.PipelineCheckpoint;
import io.tacticl.data.pipeline.repository.PipelineCheckpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramCheckpointResolverTest {

    private PipelineCheckpointRepository checkpointRepo;
    private PdlcV2Service pdlcV2Service;
    private TelegramCheckpointResolver resolver;

    @BeforeEach
    void setUp() {
        checkpointRepo = mock(PipelineCheckpointRepository.class);
        pdlcV2Service = mock(PdlcV2Service.class);
        resolver = new TelegramCheckpointResolver(checkpointRepo, pdlcV2Service);
    }

    @Test
    void approveActionMapsToAPPROVEDAndInvokesPdlcWithCorrectArgs() {
        PipelineCheckpoint cp = mock(PipelineCheckpoint.class);
        when(cp.getSparkId()).thenReturn("spark-1");
        when(checkpointRepo.findById("cp-1")).thenReturn(Optional.of(cp));

        TelegramCheckpointResolver.Result result = resolver.resolve("user-1", "cp-1", "approve");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.decision()).isEqualTo(CheckpointDecision.APPROVED);
        assertThat(result.sparkId()).isEqualTo("spark-1");
        verify(pdlcV2Service).resolveCheckpoint(
            eq("user-1"), eq("spark-1"), eq("cp-1"), eq(CheckpointDecision.APPROVED), isNull());
    }

    @Test
    void changesActionMapsToREWORK() {
        PipelineCheckpoint cp = mock(PipelineCheckpoint.class);
        when(cp.getSparkId()).thenReturn("spark-2");
        when(checkpointRepo.findById("cp-2")).thenReturn(Optional.of(cp));

        TelegramCheckpointResolver.Result result = resolver.resolve("user-1", "cp-2", "changes");

        assertThat(result.decision()).isEqualTo(CheckpointDecision.REWORK);
        verify(pdlcV2Service).resolveCheckpoint(
            eq("user-1"), eq("spark-2"), eq("cp-2"), eq(CheckpointDecision.REWORK), isNull());
    }

    @Test
    void rejectActionMapsToCANCEL() {
        PipelineCheckpoint cp = mock(PipelineCheckpoint.class);
        when(cp.getSparkId()).thenReturn("spark-3");
        when(checkpointRepo.findById("cp-3")).thenReturn(Optional.of(cp));

        TelegramCheckpointResolver.Result result = resolver.resolve("user-1", "cp-3", "reject");

        assertThat(result.decision()).isEqualTo(CheckpointDecision.CANCEL);
        verify(pdlcV2Service).resolveCheckpoint(
            eq("user-1"), eq("spark-3"), eq("cp-3"), eq(CheckpointDecision.CANCEL), isNull());
    }

    @Test
    void unknownActionReturnsInvalidActionAndSkipsResolve() {
        TelegramCheckpointResolver.Result result = resolver.resolve("user-1", "cp-1", "bogus");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.code()).isEqualTo(TelegramCheckpointResolver.ResultCode.INVALID_ACTION);
        verify(checkpointRepo, never()).findById(anyString());
        verify(pdlcV2Service, never()).resolveCheckpoint(any(), any(), any(), any(), any());
    }

    @Test
    void missingCheckpointReturnsNotFound() {
        when(checkpointRepo.findById("cp-missing")).thenReturn(Optional.empty());

        TelegramCheckpointResolver.Result result = resolver.resolve("user-1", "cp-missing", "approve");

        assertThat(result.code()).isEqualTo(TelegramCheckpointResolver.ResultCode.NOT_FOUND);
        verify(pdlcV2Service, never()).resolveCheckpoint(any(), any(), any(), any(), any());
    }

    @Test
    void pdlcFailureReturnsErrorResult() {
        PipelineCheckpoint cp = mock(PipelineCheckpoint.class);
        when(cp.getSparkId()).thenReturn("spark-1");
        when(checkpointRepo.findById("cp-1")).thenReturn(Optional.of(cp));
        doThrow(new IllegalStateException("arbiter down"))
            .when(pdlcV2Service).resolveCheckpoint(any(), any(), any(), any(), any());

        TelegramCheckpointResolver.Result result = resolver.resolve("user-1", "cp-1", "approve");

        assertThat(result.code()).isEqualTo(TelegramCheckpointResolver.ResultCode.ERROR);
    }
}
