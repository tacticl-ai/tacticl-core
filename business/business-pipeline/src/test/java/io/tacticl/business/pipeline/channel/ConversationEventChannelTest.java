package io.tacticl.business.pipeline.channel;

import io.tacticl.business.pipeline.dto.PipelineCallbackEvent;
import io.tacticl.data.conversation.entity.ConversationMessage;
import io.tacticl.data.conversation.entity.ConversationSession;
import io.tacticl.data.conversation.entity.SessionStatus;
import io.tacticl.data.conversation.repository.ConversationSessionRepository;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.repository.PipelineRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationEventChannelTest {

    @Mock ConversationSessionRepository sessionRepo;
    @Mock PipelineRunRepository runRepo;
    ConversationEventChannel channel;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        channel = new ConversationEventChannel(sessionRepo, runRepo);
    }

    @Test
    void roleStarted_appendsAssistantTurn() {
        ConversationSession session = ConversationSession.createForTelegramGroup(
            "u-1", "p-1", "build X");
        session.markProposing("CODE", "plan");
        session.markActive("spark-1");
        PipelineRun run = mock(PipelineRun.class);
        when(run.getSparkId()).thenReturn("spark-1");
        when(runRepo.findById("run-1")).thenReturn(Optional.of(run));
        when(sessionRepo.findBySparkId("spark-1")).thenReturn(Optional.of(session));

        channel.emit(new PipelineCallbackEvent(
            "run-1", "ROLE_STARTED", "PM", "PM", null));

        ConversationMessage last = session.getMessages().get(session.getMessages().size() - 1);
        assertThat(last.getContent()).contains("PM").contains("working");
        verify(sessionRepo).save(session);
    }

    @Test
    void pipelineCompleted_marksSessionCompleted() {
        ConversationSession session = ConversationSession.createForTelegramGroup(
            "u-1", "p-1", "build X");
        session.markProposing("CODE", "plan");
        session.markActive("spark-1");
        PipelineRun run = mock(PipelineRun.class);
        when(run.getSparkId()).thenReturn("spark-1");
        when(runRepo.findById("run-1")).thenReturn(Optional.of(run));
        when(sessionRepo.findBySparkId("spark-1")).thenReturn(Optional.of(session));

        channel.emit(new PipelineCallbackEvent(
            "run-1", "PIPELINE_COMPLETED", null, null, null));

        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        verify(sessionRepo).save(session);
    }

    @Test
    void missingRun_isNoop() {
        when(runRepo.findById(any())).thenReturn(Optional.empty());

        channel.emit(new PipelineCallbackEvent(
            "run-x", "ROLE_STARTED", "PM", "PM", null));

        verify(sessionRepo, never()).save(any());
    }

    @Test
    void missingSession_isNoop() {
        PipelineRun run = mock(PipelineRun.class);
        when(run.getSparkId()).thenReturn("spark-1");
        when(runRepo.findById("run-1")).thenReturn(Optional.of(run));
        when(sessionRepo.findBySparkId("spark-1")).thenReturn(Optional.empty());

        channel.emit(new PipelineCallbackEvent(
            "run-1", "ROLE_STARTED", "PM", "PM", null));

        verify(sessionRepo, never()).save(any());
    }

    @Test
    void checkpointRequested_appendsButDoesNotMarkCompleted() {
        ConversationSession session = ConversationSession.createForTelegramGroup(
            "u-1", "p-1", "build X");
        session.markProposing("CODE", "plan");
        session.markActive("spark-1");
        PipelineRun run = mock(PipelineRun.class);
        when(run.getSparkId()).thenReturn("spark-1");
        when(runRepo.findById("run-1")).thenReturn(Optional.of(run));
        when(sessionRepo.findBySparkId("spark-1")).thenReturn(Optional.of(session));

        channel.emit(new PipelineCallbackEvent(
            "run-1", "CHECKPOINT_REQUESTED", "REVIEWER", "REVIEWER", null));

        ConversationMessage last = session.getMessages().get(session.getMessages().size() - 1);
        assertThat(last.getContent()).contains("Checkpoint").contains("REVIEWER");
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        verify(sessionRepo).save(session);
    }

    @Test
    void terminalRedeliveryOnAlreadyCompletedSession_appendsMessageButDoesNotReassertStatus() {
        ConversationSession session = ConversationSession.createForTelegramGroup(
            "u-1", "p-1", "build X");
        session.markProposing("CODE", "plan");
        session.markActive("spark-1");
        session.markCompleted();
        java.time.Instant priorUpdatedAt = session.getUpdatedAt();
        PipelineRun run = mock(PipelineRun.class);
        when(run.getSparkId()).thenReturn("spark-1");
        when(runRepo.findById("run-1")).thenReturn(Optional.of(run));
        when(sessionRepo.findBySparkId("spark-1")).thenReturn(Optional.of(session));

        channel.emit(new PipelineCallbackEvent(
            "run-1", "PIPELINE_COMPLETED", null, null, null));

        // Status stays COMPLETED, message still appended for audit trail
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getUpdatedAt()).isAfterOrEqualTo(priorUpdatedAt);
        verify(sessionRepo).save(session);
    }
}
