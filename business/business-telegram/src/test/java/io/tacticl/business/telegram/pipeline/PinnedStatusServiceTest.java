package io.tacticl.business.telegram.pipeline;

import io.tacticl.business.telegram.command.ProjectPipelineSummaryProvider;
import io.tacticl.business.telegram.command.ProjectPipelineSummaryProvider.ProjectPipelineSummary;
import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.client.telegram.dto.SendMessageResponse;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PinnedStatusServiceTest {

    private static final long CHAT_ID = -1001L;
    private static final long CHAT_B = -2002L;
    private static final String PROJECT_ID = "project-1";
    private static final String PROJECT_B = "project-2";

    private TelegramBotClient bot;
    private TelegramProjectLinkRepository projectRepo;
    private ProjectPipelineSummaryProvider summaryProvider;
    private MutableClock clock;
    private PinnedStatusService service;

    @BeforeEach
    void setUp() {
        bot = mock(TelegramBotClient.class);
        projectRepo = mock(TelegramProjectLinkRepository.class);
        summaryProvider = mock(ProjectPipelineSummaryProvider.class);
        clock = new MutableClock(Instant.parse("2026-04-22T10:00:00Z"));
        service = new PinnedStatusService(bot, projectRepo, Optional.of(summaryProvider), clock);
    }

    @Test
    void firstRequest_sendsAndPinsAndPersistsMessageId() {
        TelegramProjectLink link = newLink(CHAT_ID, PROJECT_ID, null);
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));
        when(summaryProvider.summarize(PROJECT_ID)).thenReturn(
            new ProjectPipelineSummary(3, Instant.parse("2026-04-22T09:55:00Z"), new BigDecimal("1.23")));
        when(bot.sendMessage(any())).thenReturn(sendResponse(555L));
        when(bot.pinChatMessage(eq(CHAT_ID), eq(555L))).thenReturn(true);

        service.requestStatusUpdate(CHAT_ID);
        clock.advanceMillis(3_000L);
        service.drain();

        ArgumentCaptor<SendMessageRequest> sent = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(bot).sendMessage(sent.capture());
        assertThat(sent.getValue().chat_id()).isEqualTo(CHAT_ID);
        assertThat(sent.getValue().text()).contains("Project status");
        verify(bot).pinChatMessage(CHAT_ID, 555L);
        verify(bot, never()).editMessageText(anyLong(), anyLong(), anyString(), any());
        assertThat(link.getPinnedStatusMessageId()).isEqualTo(555L);
        verify(projectRepo).save(link);
    }

    @Test
    void subsequentRequest_editsExistingPin() {
        TelegramProjectLink link = newLink(CHAT_ID, PROJECT_ID, 555L);
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));
        when(summaryProvider.summarize(PROJECT_ID)).thenReturn(
            new ProjectPipelineSummary(1, Instant.parse("2026-04-22T09:59:00Z"), new BigDecimal("0.50")));

        service.requestStatusUpdate(CHAT_ID);
        clock.advanceMillis(3_000L);
        service.drain();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(bot).editMessageText(eq(CHAT_ID), eq(555L), text.capture(), isNull());
        assertThat(text.getValue()).contains("Project status").contains("Active sparks: 1");
        verify(bot, never()).sendMessage(any());
        verify(bot, never()).pinChatMessage(anyLong(), anyLong());
        verify(projectRepo, never()).save(any());
    }

    @Test
    void twoRapidRequests_coalescesToOneEdit() {
        TelegramProjectLink link = newLink(CHAT_ID, PROJECT_ID, 555L);
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));
        when(summaryProvider.summarize(PROJECT_ID)).thenReturn(
            new ProjectPipelineSummary(2, Instant.now(), BigDecimal.ZERO));

        service.requestStatusUpdate(CHAT_ID);
        clock.advanceMillis(500L);
        service.requestStatusUpdate(CHAT_ID);
        // Quiet window not yet elapsed — drain should no-op.
        service.drain();
        verify(bot, never()).editMessageText(anyLong(), anyLong(), anyString(), any());
        // Advance past quiet window (2s since last request).
        clock.advanceMillis(2_100L);
        service.drain();

        verify(bot, times(1)).editMessageText(eq(CHAT_ID), eq(555L), anyString(), isNull());
    }

    @Test
    void burstExceedingWindow_fivesUnderTenSeconds_stillOneEdit() {
        TelegramProjectLink link = newLink(CHAT_ID, PROJECT_ID, 555L);
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));
        when(summaryProvider.summarize(PROJECT_ID)).thenReturn(
            new ProjectPipelineSummary(5, Instant.now(), BigDecimal.ZERO));

        for (int i = 0; i < 5; i++) {
            service.requestStatusUpdate(CHAT_ID);
            clock.advanceMillis(1_000L);
            service.drain(); // every drain within burst should be no-op
        }
        verify(bot, never()).editMessageText(anyLong(), anyLong(), anyString(), any());
        // Let quiet window elapse.
        clock.advanceMillis(2_100L);
        service.drain();

        verify(bot, times(1)).editMessageText(eq(CHAT_ID), eq(555L), anyString(), isNull());
    }

    @Test
    void continuousActivity_hitsHardCapAndFlushes() {
        TelegramProjectLink link = newLink(CHAT_ID, PROJECT_ID, 555L);
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));
        when(summaryProvider.summarize(PROJECT_ID)).thenReturn(
            new ProjectPipelineSummary(7, Instant.now(), BigDecimal.ZERO));

        // Request every 1.5s for 12s — quiet window never elapses (2s threshold),
        // but hard cap (10s from firstRequestedAt) must still force a flush.
        long elapsedMs = 0;
        boolean flushedSeen = false;
        while (elapsedMs < 12_000L) {
            service.requestStatusUpdate(CHAT_ID);
            clock.advanceMillis(1_500L);
            elapsedMs += 1_500L;
            service.drain();
        }

        verify(bot, times(1)).editMessageText(eq(CHAT_ID), eq(555L), anyString(), isNull());
    }

    @Test
    void noActiveLink_dropsPending() {
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.empty());

        service.requestStatusUpdate(CHAT_ID);
        clock.advanceMillis(3_000L);
        service.drain();

        verify(bot, never()).sendMessage(any());
        verify(bot, never()).editMessageText(anyLong(), anyLong(), anyString(), any());
        verify(bot, never()).pinChatMessage(anyLong(), anyLong());
        // Second drain — pending should be cleared, no repeat lookup.
        service.drain();
        verify(projectRepo, times(1)).findByChatIdAndIsActiveTrue(CHAT_ID);
    }

    @Test
    void summaryProviderEmpty_dropsPending() {
        service = new PinnedStatusService(bot, projectRepo, Optional.empty(), clock);
        TelegramProjectLink link = newLink(CHAT_ID, PROJECT_ID, null);
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));

        service.requestStatusUpdate(CHAT_ID);
        clock.advanceMillis(3_000L);
        service.drain();

        verifyNoInteractions(bot);
    }

    @Test
    void summaryProviderReturnsNull_dropsPending() {
        TelegramProjectLink link = newLink(CHAT_ID, PROJECT_ID, null);
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));
        when(summaryProvider.summarize(PROJECT_ID)).thenReturn(null);

        service.requestStatusUpdate(CHAT_ID);
        clock.advanceMillis(3_000L);
        service.drain();

        verifyNoInteractions(bot);
    }

    @Test
    void botThrowsOnSend_swallowsAndClearsPending() {
        TelegramProjectLink link = newLink(CHAT_ID, PROJECT_ID, null);
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));
        when(summaryProvider.summarize(PROJECT_ID)).thenReturn(
            new ProjectPipelineSummary(0, Instant.now(), BigDecimal.ZERO));
        when(bot.sendMessage(any())).thenThrow(new RuntimeException("boom"));

        service.requestStatusUpdate(CHAT_ID);
        clock.advanceMillis(3_000L);
        service.drain();

        verify(projectRepo, never()).save(any());
        verify(bot, never()).editMessageText(anyLong(), anyLong(), anyString(), any());
        // Drain again; pending must be cleared so no further findByChatId lookup.
        service.drain();
        verify(projectRepo, times(1)).findByChatIdAndIsActiveTrue(CHAT_ID);
    }

    @Test
    void botThrowsOnEdit_swallowsAndClearsPending() {
        TelegramProjectLink link = newLink(CHAT_ID, PROJECT_ID, 555L);
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));
        when(summaryProvider.summarize(PROJECT_ID)).thenReturn(
            new ProjectPipelineSummary(1, Instant.now(), BigDecimal.ZERO));
        when(bot.editMessageText(anyLong(), anyLong(), anyString(), any()))
            .thenThrow(new RuntimeException("boom"));

        service.requestStatusUpdate(CHAT_ID);
        clock.advanceMillis(3_000L);
        service.drain();

        verify(projectRepo, never()).save(any());
        service.drain();
        verify(projectRepo, times(1)).findByChatIdAndIsActiveTrue(CHAT_ID);
    }

    @Test
    void multipleChats_drainInOneTick() {
        TelegramProjectLink a = newLink(CHAT_ID, PROJECT_ID, 555L);
        TelegramProjectLink b = newLink(CHAT_B, PROJECT_B, 777L);
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(a));
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_B)).thenReturn(Optional.of(b));
        when(summaryProvider.summarize(anyString())).thenReturn(
            new ProjectPipelineSummary(1, Instant.now(), BigDecimal.ZERO));

        service.requestStatusUpdate(CHAT_ID);
        service.requestStatusUpdate(CHAT_B);
        clock.advanceMillis(3_000L);
        service.drain();

        verify(bot).editMessageText(eq(CHAT_ID), eq(555L), anyString(), isNull());
        verify(bot).editMessageText(eq(CHAT_B), eq(777L), anyString(), isNull());
    }

    @Test
    void oneChatThrowing_doesNotBlockOther() {
        TelegramProjectLink a = newLink(CHAT_ID, PROJECT_ID, 555L);
        TelegramProjectLink b = newLink(CHAT_B, PROJECT_B, 777L);
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(a));
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_B)).thenReturn(Optional.of(b));
        when(summaryProvider.summarize(anyString())).thenReturn(
            new ProjectPipelineSummary(1, Instant.now(), BigDecimal.ZERO));
        when(bot.editMessageText(eq(CHAT_ID), anyLong(), anyString(), any()))
            .thenThrow(new RuntimeException("boom"));

        service.requestStatusUpdate(CHAT_ID);
        service.requestStatusUpdate(CHAT_B);
        clock.advanceMillis(3_000L);
        service.drain();

        verify(bot).editMessageText(eq(CHAT_B), eq(777L), anyString(), isNull());
    }

    @Test
    void drainWithNothingPending_isNoop() {
        service.drain();
        verifyNoInteractions(bot);
        verifyNoInteractions(projectRepo);
    }

    private static TelegramProjectLink newLink(long chatId, String projectId, Long pinnedId) {
        TelegramProjectLink link = TelegramProjectLink.create(projectId, chatId, "user-1", "group");
        link.setPinnedStatusMessageId(pinnedId);
        return link;
    }

    private static SendMessageResponse sendResponse(long messageId) {
        return new SendMessageResponse(messageId, null, 0L, "");
    }

    /** Test-local clock — advance explicitly to drive debouncer without Thread.sleep. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) { this.now = start; }

        void advanceMillis(long ms) { now = now.plusMillis(ms); }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        @Override public long millis() { return now.toEpochMilli(); }
    }
}
