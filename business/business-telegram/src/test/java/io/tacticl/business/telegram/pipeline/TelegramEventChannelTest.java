package io.tacticl.business.telegram.pipeline;

import io.tacticl.business.pipeline.dto.PipelineCallbackEvent;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.pipeline.entity.PdlcRole;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.repository.PipelineRunRepository;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.repository.SparkRepository;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramEventChannelTest {

    private static final String RUN_ID = "run-1";
    private static final String USER_ID = "user-1";
    private static final String SPARK_ID = "spark-1";
    private static final String PROJECT_ID = "project-1";
    private static final long CHAT_ID = 999_000L;

    @Mock private PipelineRunRepository runRepo;
    @Mock private SparkRepository sparkRepo;
    @Mock private TelegramProjectLinkRepository linkRepo;
    @Mock private TelegramOutboundQueue queue;

    private TelegramMessageFormatter formatter;
    private TelegramEventChannel channel;

    @BeforeEach
    void setUp() {
        formatter = new TelegramMessageFormatter();
        channel = new TelegramEventChannel(runRepo, sparkRepo, linkRepo, formatter, queue);
    }

    @Test
    void missingPipelineRun_skipsSilently() {
        when(runRepo.findById(RUN_ID)).thenReturn(Optional.empty());

        channel.emit(event("ROLE_STARTED", "RESEARCHER"));

        verifyNoInteractions(sparkRepo, linkRepo, queue);
    }

    @Test
    void sparkNotFound_skipsSilently() {
        // WHY: cover the branch where the run exists but the spark lookup misses
        // (e.g., user-id skew or spark deleted mid-run). Must not touch the link repo.
        stubRun();
        when(sparkRepo.findByIdAndUserId(SPARK_ID, USER_ID)).thenReturn(Optional.empty());

        channel.emit(event("ROLE_STARTED", "RESEARCHER"));

        verifyNoInteractions(linkRepo, queue);
    }

    @Test
    void sparkWithoutProjectId_skipsSilently() {
        stubRun();
        Spark spark = Spark.create(USER_ID, "do things");
        when(sparkRepo.findByIdAndUserId(SPARK_ID, USER_ID)).thenReturn(Optional.of(spark));

        channel.emit(event("ROLE_STARTED", "RESEARCHER"));

        verifyNoInteractions(linkRepo, queue);
    }

    @Test
    void noTelegramLink_skipsSilently() {
        stubRun();
        stubSparkWithProject();
        when(linkRepo.findByProjectIdAndIsActiveTrue(PROJECT_ID)).thenReturn(Optional.empty());

        channel.emit(event("ROLE_STARTED", "RESEARCHER"));

        verifyNoInteractions(queue);
    }

    @Test
    void archivedLink_skipsSilently() {
        stubRun();
        stubSparkWithProject();
        TelegramProjectLink link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, USER_ID, "My Group");
        link.archive();
        when(linkRepo.findByProjectIdAndIsActiveTrue(PROJECT_ID)).thenReturn(Optional.of(link));

        channel.emit(event("ROLE_STARTED", "RESEARCHER"));

        verifyNoInteractions(queue);
    }

    @Test
    void activeLinkNoForumTopics_enqueuesWithNullThreadId() {
        stubRun();
        stubSparkWithProject();
        when(linkRepo.findByProjectIdAndIsActiveTrue(PROJECT_ID))
            .thenReturn(Optional.of(TelegramProjectLink.create(PROJECT_ID, CHAT_ID, USER_ID, "My Group")));
        when(queue.enqueue(anyLong(), any(OutboundMessage.class))).thenReturn(true);

        channel.emit(event("ROLE_STARTED", "RESEARCHER"));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(queue).enqueue(eq(CHAT_ID), captor.capture());
        SendMessageRequest req = captor.getValue().request();
        assertThat(req.chat_id()).isEqualTo(CHAT_ID);
        assertThat(req.message_thread_id()).isNull();
        assertThat(req.text()).contains("RESEARCHER");
    }

    @Test
    void activeLinkWithForumTopics_routesToRoleThread() {
        stubRun();
        stubSparkWithProject();
        TelegramProjectLink link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, USER_ID, "My Group");
        link.setForumTopics(Map.of(PdlcRole.RESEARCHER, 42L));
        when(linkRepo.findByProjectIdAndIsActiveTrue(PROJECT_ID)).thenReturn(Optional.of(link));
        when(queue.enqueue(anyLong(), any(OutboundMessage.class))).thenReturn(true);

        channel.emit(event("ROLE_STARTED", "RESEARCHER"));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(queue).enqueue(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().request().message_thread_id()).isEqualTo(42);
    }

    @Test
    void unknownRole_fallsBackToGeneralChatThreadNull() {
        stubRun();
        stubSparkWithProject();
        TelegramProjectLink link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, USER_ID, "My Group");
        link.setForumTopics(Map.of(PdlcRole.RESEARCHER, 42L));
        when(linkRepo.findByProjectIdAndIsActiveTrue(PROJECT_ID)).thenReturn(Optional.of(link));
        when(queue.enqueue(anyLong(), any(OutboundMessage.class))).thenReturn(true);

        channel.emit(event("ROLE_STARTED", "ARCHITECT"));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(queue).enqueue(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().request().message_thread_id()).isNull();
    }

    @Test
    void formatterReturnsEmpty_noEnqueue() {
        stubRun();
        stubSparkWithProject();
        when(linkRepo.findByProjectIdAndIsActiveTrue(PROJECT_ID))
            .thenReturn(Optional.of(TelegramProjectLink.create(PROJECT_ID, CHAT_ID, USER_ID, "My Group")));

        channel.emit(event("UNKNOWN_EVENT_TYPE", "RESEARCHER"));

        verifyNoInteractions(queue);
    }

    @Test
    void formatterReturnsMultipleMessages_allEnqueuedInOrder() {
        stubRun();
        stubSparkWithProject();
        when(linkRepo.findByProjectIdAndIsActiveTrue(PROJECT_ID))
            .thenReturn(Optional.of(TelegramProjectLink.create(PROJECT_ID, CHAT_ID, USER_ID, "My Group")));

        TelegramMessageFormatter multiFormatter = mock(TelegramMessageFormatter.class);
        SendMessageRequest m1 = SendMessageRequest.plain(CHAT_ID, "first");
        SendMessageRequest m2 = SendMessageRequest.plain(CHAT_ID, "second");
        when(multiFormatter.format(eq(CHAT_ID), eq(null), any(PipelineCallbackEvent.class)))
            .thenReturn(List.of(m1, m2));
        when(queue.enqueue(anyLong(), any(OutboundMessage.class))).thenReturn(true);

        TelegramEventChannel c = new TelegramEventChannel(runRepo, sparkRepo, linkRepo, multiFormatter, queue);
        c.emit(event("ROLE_STARTED", "RESEARCHER"));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(queue, times(2)).enqueue(eq(CHAT_ID), captor.capture());
        assertThat(captor.getAllValues()).extracting(m -> m.request().text())
            .containsExactly("first", "second");
    }

    @Test
    void payloadWithoutRoleField_threadIdNull() {
        stubRun();
        stubSparkWithProject();
        TelegramProjectLink link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, USER_ID, "My Group");
        link.setForumTopics(Map.of(PdlcRole.RESEARCHER, 42L));
        when(linkRepo.findByProjectIdAndIsActiveTrue(PROJECT_ID)).thenReturn(Optional.of(link));
        when(queue.enqueue(anyLong(), any(OutboundMessage.class))).thenReturn(true);

        // PIPELINE_STARTED carries no role — threadId must default to null.
        channel.emit(new PipelineCallbackEvent(RUN_ID, "PIPELINE_STARTED", null, null, null));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(queue).enqueue(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().request().message_thread_id()).isNull();
    }

    @Test
    void queueFullOnEnqueue_logsAndContinuesWithoutThrowing() {
        // WHY: queue back-pressure — returning false from enqueue must be logged
        // and not raise to the caller.
        stubRun();
        stubSparkWithProject();
        when(linkRepo.findByProjectIdAndIsActiveTrue(PROJECT_ID))
            .thenReturn(Optional.of(TelegramProjectLink.create(PROJECT_ID, CHAT_ID, USER_ID, "My Group")));
        when(queue.enqueue(anyLong(), any(OutboundMessage.class))).thenReturn(false);

        channel.emit(event("ROLE_STARTED", "RESEARCHER"));

        verify(queue).enqueue(eq(CHAT_ID), any(OutboundMessage.class));
        // Does not throw — that's the contract.
    }

    @Test
    void destinationCache_secondEmitSkipsMongoLookups() {
        // WHY: with the new cache, back-to-back events for the same run must not
        // repeat the 3 Mongo reads. Verify each repo is hit at most once.
        stubRun();
        stubSparkWithProject();
        when(linkRepo.findByProjectIdAndIsActiveTrue(PROJECT_ID))
            .thenReturn(Optional.of(TelegramProjectLink.create(PROJECT_ID, CHAT_ID, USER_ID, "My Group")));
        when(queue.enqueue(anyLong(), any(OutboundMessage.class))).thenReturn(true);

        channel.emit(event("ROLE_STARTED", "RESEARCHER"));
        channel.emit(event("ROLE_COMPLETED", "RESEARCHER"));
        channel.emit(event("ROLE_STARTED", "ARCHITECT"));

        verify(runRepo, times(1)).findById(RUN_ID);
        verify(sparkRepo, times(1)).findByIdAndUserId(SPARK_ID, USER_ID);
        verify(linkRepo, times(1)).findByProjectIdAndIsActiveTrue(PROJECT_ID);
        // All three events still produce an enqueue.
        verify(queue, times(3)).enqueue(eq(CHAT_ID), any(OutboundMessage.class));
    }

    @Test
    void destinationCache_emptyDestinationAlsoCached() {
        // WHY: negative resolution (no spark / no link) must also cache so cloud sparks
        // don't re-query Mongo 3× per event.
        stubRun();
        Spark spark = Spark.create(USER_ID, "do things");
        // No projectId — cloud spark.
        when(sparkRepo.findByIdAndUserId(SPARK_ID, USER_ID)).thenReturn(Optional.of(spark));

        channel.emit(event("ROLE_STARTED", "RESEARCHER"));
        channel.emit(event("ROLE_COMPLETED", "RESEARCHER"));

        verify(runRepo, times(1)).findById(RUN_ID);
        verify(sparkRepo, times(1)).findByIdAndUserId(SPARK_ID, USER_ID);
        verifyNoInteractions(linkRepo, queue);
    }

    @Test
    void completeEvictsCache() {
        // WHY: terminal event must drop the cached entry — otherwise late stray events
        // for the same run would re-use a stale destination.
        stubRun();
        stubSparkWithProject();
        when(linkRepo.findByProjectIdAndIsActiveTrue(PROJECT_ID))
            .thenReturn(Optional.of(TelegramProjectLink.create(PROJECT_ID, CHAT_ID, USER_ID, "My Group")));
        when(queue.enqueue(anyLong(), any(OutboundMessage.class))).thenReturn(true);

        channel.emit(event("ROLE_STARTED", "RESEARCHER"));
        channel.complete(RUN_ID);
        channel.emit(event("PIPELINE_COMPLETED", null));

        // Cache got cleared on complete(), so the next emit re-resolves.
        verify(runRepo, times(2)).findById(RUN_ID);
    }

    @Test
    void nullEventIsNoOp() {
        channel.emit((PipelineCallbackEvent) null);
        verifyNoInteractions(runRepo, sparkRepo, linkRepo, queue);
    }

    @Test
    void nullPipelineRunIdIsNoOp() {
        channel.emit(new PipelineCallbackEvent(null, "ROLE_STARTED", "PM", null, null));
        verifyNoInteractions(runRepo, sparkRepo, linkRepo, queue);
    }

    // ---- helpers -----------------------------------------------------------

    private static PipelineCallbackEvent event(String type, String role) {
        return new PipelineCallbackEvent(RUN_ID, type, role, role, null);
    }

    private void stubRun() {
        PipelineRun run = PipelineRun.create(USER_ID, SPARK_ID, "do it", "repo", "FULL_PDLC", List.of(), 50.0);
        when(runRepo.findById(RUN_ID)).thenReturn(Optional.of(run));
    }

    private void stubSparkWithProject() {
        Spark spark = Spark.create(USER_ID, "do it");
        spark.setProjectId(PROJECT_ID);
        when(sparkRepo.findByIdAndUserId(SPARK_ID, USER_ID)).thenReturn(Optional.of(spark));
    }
}
