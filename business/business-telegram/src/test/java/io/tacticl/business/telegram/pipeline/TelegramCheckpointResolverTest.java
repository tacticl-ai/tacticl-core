package io.tacticl.business.telegram.pipeline;

import io.tacticl.business.pipeline.service.PdlcV2Service;
import io.tacticl.data.pipeline.entity.CheckpointDecision;
import io.tacticl.data.pipeline.entity.PipelineCheckpoint;
import io.tacticl.data.pipeline.repository.PipelineCheckpointRepository;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.repository.SparkRepository;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
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

    private static final long CHAT_ID = -1001L;
    private static final String USER_ID = "user-1";
    private static final String PROJECT_ID = "project-1";

    private PipelineCheckpointRepository checkpointRepo;
    private SparkRepository sparkRepo;
    private TelegramProjectLinkRepository linkRepo;
    private PdlcV2Service pdlcV2Service;
    private TelegramCheckpointResolver resolver;

    @BeforeEach
    void setUp() {
        checkpointRepo = mock(PipelineCheckpointRepository.class);
        sparkRepo = mock(SparkRepository.class);
        linkRepo = mock(TelegramProjectLinkRepository.class);
        pdlcV2Service = mock(PdlcV2Service.class);
        resolver = new TelegramCheckpointResolver(checkpointRepo, sparkRepo, linkRepo, pdlcV2Service);
    }

    @Test
    void approveActionMapsToAPPROVEDAndInvokesPdlcWithCorrectArgs() {
        stubCheckpointAndScope("cp-1", "spark-1", PROJECT_ID, PROJECT_ID);

        TelegramCheckpointResolver.Result result = resolver.resolve(CHAT_ID, USER_ID, "cp-1", "approve");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.decision()).isEqualTo(CheckpointDecision.APPROVED);
        assertThat(result.sparkId()).isEqualTo("spark-1");
        verify(pdlcV2Service).resolveCheckpoint(
            eq(USER_ID), eq("spark-1"), eq("cp-1"), eq(CheckpointDecision.APPROVED), isNull());
    }

    @Test
    void changesActionMapsToREWORK() {
        stubCheckpointAndScope("cp-2", "spark-2", PROJECT_ID, PROJECT_ID);

        TelegramCheckpointResolver.Result result = resolver.resolve(CHAT_ID, USER_ID, "cp-2", "changes");

        assertThat(result.decision()).isEqualTo(CheckpointDecision.REWORK);
        verify(pdlcV2Service).resolveCheckpoint(
            eq(USER_ID), eq("spark-2"), eq("cp-2"), eq(CheckpointDecision.REWORK), isNull());
    }

    @Test
    void rejectActionMapsToCANCEL() {
        stubCheckpointAndScope("cp-3", "spark-3", PROJECT_ID, PROJECT_ID);

        TelegramCheckpointResolver.Result result = resolver.resolve(CHAT_ID, USER_ID, "cp-3", "reject");

        assertThat(result.decision()).isEqualTo(CheckpointDecision.CANCEL);
        verify(pdlcV2Service).resolveCheckpoint(
            eq(USER_ID), eq("spark-3"), eq("cp-3"), eq(CheckpointDecision.CANCEL), isNull());
    }

    @Test
    void unknownActionReturnsInvalidActionAndSkipsResolve() {
        TelegramCheckpointResolver.Result result = resolver.resolve(CHAT_ID, USER_ID, "cp-1", "bogus");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.code()).isEqualTo(TelegramCheckpointResolver.ResultCode.INVALID_ACTION);
        verify(checkpointRepo, never()).findById(anyString());
        verify(pdlcV2Service, never()).resolveCheckpoint(any(), any(), any(), any(), any());
    }

    @Test
    void missingCheckpointReturnsNotFound() {
        when(checkpointRepo.findById("cp-missing")).thenReturn(Optional.empty());

        TelegramCheckpointResolver.Result result = resolver.resolve(CHAT_ID, USER_ID, "cp-missing", "approve");

        assertThat(result.code()).isEqualTo(TelegramCheckpointResolver.ResultCode.NOT_FOUND);
        verify(pdlcV2Service, never()).resolveCheckpoint(any(), any(), any(), any(), any());
    }

    @Test
    void pdlcFailureReturnsErrorResult() {
        stubCheckpointAndScope("cp-1", "spark-1", PROJECT_ID, PROJECT_ID);
        doThrow(new IllegalStateException("arbiter down"))
            .when(pdlcV2Service).resolveCheckpoint(any(), any(), any(), any(), any());

        TelegramCheckpointResolver.Result result = resolver.resolve(CHAT_ID, USER_ID, "cp-1", "approve");

        assertThat(result.code()).isEqualTo(TelegramCheckpointResolver.ResultCode.ERROR);
    }

    @Test
    void sparkMissing_isForbidden() {
        // WHY: if the checkpoint references a spark that no longer exists we can't scope-check it;
        // refuse defensively rather than let the underlying service decide.
        PipelineCheckpoint cp = mock(PipelineCheckpoint.class);
        when(cp.getSparkId()).thenReturn("spark-ghost");
        when(checkpointRepo.findById("cp-1")).thenReturn(Optional.of(cp));
        when(sparkRepo.findById("spark-ghost")).thenReturn(Optional.empty());

        TelegramCheckpointResolver.Result result = resolver.resolve(CHAT_ID, USER_ID, "cp-1", "approve");

        assertThat(result.code()).isEqualTo(TelegramCheckpointResolver.ResultCode.FORBIDDEN);
        verify(pdlcV2Service, never()).resolveCheckpoint(any(), any(), any(), any(), any());
    }

    @Test
    void sparkWithoutProjectId_isForbidden() {
        // WHY: cloud spark checkpoint has no projectId so cannot be resolved from a chat.
        PipelineCheckpoint cp = mock(PipelineCheckpoint.class);
        when(cp.getSparkId()).thenReturn("spark-1");
        when(checkpointRepo.findById("cp-1")).thenReturn(Optional.of(cp));
        Spark spark = Spark.create(USER_ID, "cloud task");
        when(sparkRepo.findById("spark-1")).thenReturn(Optional.of(spark));

        TelegramCheckpointResolver.Result result = resolver.resolve(CHAT_ID, USER_ID, "cp-1", "approve");

        assertThat(result.code()).isEqualTo(TelegramCheckpointResolver.ResultCode.FORBIDDEN);
        verify(pdlcV2Service, never()).resolveCheckpoint(any(), any(), any(), any(), any());
    }

    @Test
    void noLinkForChat_isForbidden() {
        PipelineCheckpoint cp = mock(PipelineCheckpoint.class);
        when(cp.getSparkId()).thenReturn("spark-1");
        when(checkpointRepo.findById("cp-1")).thenReturn(Optional.of(cp));
        Spark spark = Spark.create(USER_ID, "do it");
        spark.setProjectId(PROJECT_ID);
        when(sparkRepo.findById("spark-1")).thenReturn(Optional.of(spark));
        when(linkRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.empty());

        TelegramCheckpointResolver.Result result = resolver.resolve(CHAT_ID, USER_ID, "cp-1", "approve");

        assertThat(result.code()).isEqualTo(TelegramCheckpointResolver.ResultCode.FORBIDDEN);
        verify(pdlcV2Service, never()).resolveCheckpoint(any(), any(), any(), any(), any());
    }

    @Test
    void crossProjectCheckpoint_isForbidden() {
        // WHY: this is the attack vector — chat is linked to project A, but the
        // checkpoint id in the callback belongs to a spark in project B.
        stubCheckpointAndScope("cp-1", "spark-1", "project-FOREIGN", PROJECT_ID);

        TelegramCheckpointResolver.Result result = resolver.resolve(CHAT_ID, USER_ID, "cp-1", "approve");

        assertThat(result.code()).isEqualTo(TelegramCheckpointResolver.ResultCode.FORBIDDEN);
        verify(pdlcV2Service, never()).resolveCheckpoint(any(), any(), any(), any(), any());
    }

    /**
     * Stubs the scope-check chain: checkpoint → spark (project=sparkProjectId) → link (project=chatProjectId).
     * Happy path uses equal ids; cross-project path feeds distinct ids to force FORBIDDEN.
     */
    private void stubCheckpointAndScope(String checkpointId, String sparkId,
                                        String sparkProjectId, String chatProjectId) {
        PipelineCheckpoint cp = mock(PipelineCheckpoint.class);
        when(cp.getSparkId()).thenReturn(sparkId);
        when(checkpointRepo.findById(checkpointId)).thenReturn(Optional.of(cp));
        Spark spark = Spark.create(USER_ID, "do it");
        spark.setProjectId(sparkProjectId);
        when(sparkRepo.findById(sparkId)).thenReturn(Optional.of(spark));
        TelegramProjectLink link = TelegramProjectLink.create(chatProjectId, CHAT_ID, USER_ID, "Group");
        when(linkRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));
    }
}
