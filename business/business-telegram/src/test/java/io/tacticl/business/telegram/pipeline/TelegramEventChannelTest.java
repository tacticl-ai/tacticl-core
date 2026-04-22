package io.tacticl.business.telegram.pipeline;

import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.data.pipeline.entity.PdlcRole;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.repository.PipelineRunRepository;
import io.tacticl.data.sparks.entity.Spark;
import io.tacticl.data.sparks.repository.SparkRepository;
import io.tacticl.data.telegram.entity.ProjectStatus;
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
import static org.mockito.Mockito.never;
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

        channel.emit(RUN_ID, "ROLE_STARTED", Map.of("role", "RESEARCHER"));

        verifyNoInteractions(sparkRepo, linkRepo, queue);
    }

    @Test
    void sparkWithoutProjectId_skipsSilently() {
        stubRun();
        Spark spark = Spark.create(USER_ID, "do things");
        // WHY: cloud/non-group sparks have no projectId — channel must be a no-op.
        when(sparkRepo.findByIdAndUserId(SPARK_ID, USER_ID)).thenReturn(Optional.of(spark));

        channel.emit(RUN_ID, "ROLE_STARTED", Map.of("role", "RESEARCHER"));

        verifyNoInteractions(linkRepo, queue);
    }

    @Test
    void noTelegramLink_skipsSilently() {
        stubRun();
        stubSparkWithProject();
        when(linkRepo.findByProjectIdAndIsActiveTrue(PROJECT_ID)).thenReturn(Optional.empty());

        channel.emit(RUN_ID, "ROLE_STARTED", Map.of("role", "RESEARCHER"));

        verifyNoInteractions(queue);
    }

    @Test
    void archivedLink_skipsSilently() {
        stubRun();
        stubSparkWithProject();
        TelegramProjectLink link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, USER_ID, "My Group");
        link.archive();
        when(linkRepo.findByProjectIdAndIsActiveTrue(PROJECT_ID)).thenReturn(Optional.of(link));

        channel.emit(RUN_ID, "ROLE_STARTED", Map.of("role", "RESEARCHER"));

        verifyNoInteractions(queue);
    }

    @Test
    void activeLinkNoForumTopics_enqueuesWithNullThreadId() {
        stubRun();
        stubSparkWithProject();
        when(linkRepo.findByProjectIdAndIsActiveTrue(PROJECT_ID))
            .thenReturn(Optional.of(TelegramProjectLink.create(PROJECT_ID, CHAT_ID, USER_ID, "My Group")));
        when(queue.enqueue(anyLong(), any(OutboundMessage.class))).thenReturn(true);

        channel.emit(RUN_ID, "ROLE_STARTED", Map.of("role", "RESEARCHER"));

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

        channel.emit(RUN_ID, "ROLE_STARTED", Map.of("role", "RESEARCHER", "phase", "DISCOVERY"));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(queue).enqueue(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().request().message_thread_id()).isEqualTo(42);
    }

    @Test
    void unknownRole_fallsBackToGeneralChatThreadNull() {
        stubRun();
        stubSparkWithProject();
        TelegramProjectLink link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, USER_ID, "My Group");
        // WHY: no mapping for ARCHITECT — must not NPE; must post to general (null threadId).
        link.setForumTopics(Map.of(PdlcRole.RESEARCHER, 42L));
        when(linkRepo.findByProjectIdAndIsActiveTrue(PROJECT_ID)).thenReturn(Optional.of(link));
        when(queue.enqueue(anyLong(), any(OutboundMessage.class))).thenReturn(true);

        channel.emit(RUN_ID, "ROLE_STARTED", Map.of("role", "ARCHITECT"));

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

        // WHY: unknown event name → formatter returns empty list → must be a no-op.
        channel.emit(RUN_ID, "UNKNOWN_EVENT_TYPE", Map.of("role", "RESEARCHER"));

        verifyNoInteractions(queue);
    }

    @Test
    void formatterReturnsMultipleMessages_allEnqueuedInOrder() {
        stubRun();
        stubSparkWithProject();
        when(linkRepo.findByProjectIdAndIsActiveTrue(PROJECT_ID))
            .thenReturn(Optional.of(TelegramProjectLink.create(PROJECT_ID, CHAT_ID, USER_ID, "My Group")));

        // WHY: guard the per-message enqueue contract. Use a spy formatter returning two messages.
        TelegramMessageFormatter multiFormatter = mock(TelegramMessageFormatter.class);
        SendMessageRequest m1 = SendMessageRequest.plain(CHAT_ID, "first");
        SendMessageRequest m2 = SendMessageRequest.plain(CHAT_ID, "second");
        when(multiFormatter.format(eq(CHAT_ID), eq(null), eq("ROLE_STARTED"), any()))
            .thenReturn(List.of(m1, m2));
        when(queue.enqueue(anyLong(), any(OutboundMessage.class))).thenReturn(true);

        TelegramEventChannel c = new TelegramEventChannel(runRepo, sparkRepo, linkRepo, multiFormatter, queue);
        c.emit(RUN_ID, "ROLE_STARTED", Map.of("role", "RESEARCHER"));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(queue, org.mockito.Mockito.times(2)).enqueue(eq(CHAT_ID), captor.capture());
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

        // WHY: PIPELINE_STARTED carries no role — threadId must default to null.
        channel.emit(RUN_ID, "PIPELINE_STARTED", Map.of());

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(queue).enqueue(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().request().message_thread_id()).isNull();
    }

    @Test
    void completeIsNoOp() {
        // WHY: default channel#complete should not touch repositories or the queue.
        channel.complete(RUN_ID);

        verifyNoInteractions(runRepo, sparkRepo, linkRepo, queue);
    }

    // ---- helpers -----------------------------------------------------------

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
