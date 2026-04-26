package io.tacticl.business.telegram.command;

import io.tacticl.business.telegram.audit.TelegramAuditLogger;
import io.tacticl.business.telegram.command.ProjectPipelineSummaryProvider.ProjectPipelineSummary;
import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.client.telegram.dto.Chat;
import io.tacticl.client.telegram.dto.Message;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatusCommandTest {

    private TelegramProjectLinkRepository projectRepo;
    private ProjectPipelineSummaryProvider summaryProvider;
    private TelegramOutboundQueue outbound;
    private TelegramIdentityResolver identity;
    private TelegramAuditLogger auditLogger;

    private static final long CHAT_ID = -100L;
    private static final long SENDER_TG_ID = 42L;
    private static final String PROJECT_ID = "proj-1";
    private static final String OWNER_ID = "user-alice";

    @BeforeEach
    void setUp() {
        projectRepo = mock(TelegramProjectLinkRepository.class);
        summaryProvider = mock(ProjectPipelineSummaryProvider.class);
        outbound = mock(TelegramOutboundQueue.class);
        identity = mock(TelegramIdentityResolver.class);
        auditLogger = mock(TelegramAuditLogger.class);
    }

    private StatusCommand withProvider() {
        return new StatusCommand(projectRepo, Optional.of(summaryProvider), outbound, identity, auditLogger);
    }

    private StatusCommand withoutProvider() {
        return new StatusCommand(projectRepo, Optional.empty(), outbound, identity, auditLogger);
    }

    private static CommandContext groupCtx(String text) {
        Chat chat = new Chat(CHAT_ID, "group", null, null, "My Group", false);
        Message msg = new Message(
                1L, 0L, chat, null, text,
                null, null, null, null,
                null, null, null, false, null);
        return new CommandContext(CHAT_ID, SENDER_TG_ID, text, "alice", msg);
    }

    private String capturedReplyText() {
        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(CHAT_ID), captor.capture());
        return captor.getValue().request().text();
    }

    @Test
    void handleNoActiveProjectReplies() {
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.empty());

        withProvider().handle(groupCtx("/status"));

        assertThat(capturedReplyText()).contains("No active project");
    }

    @Test
    void handleNoProviderRepliesNotEnabled() {
        TelegramProjectLink link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, OWNER_ID, "My Group");
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));

        withoutProvider().handle(groupCtx("/status"));

        assertThat(capturedReplyText()).containsIgnoringCase("not yet enabled");
    }

    @Test
    void handleProviderReturnsNullRepliesNotEnabled() {
        TelegramProjectLink link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, OWNER_ID, "My Group");
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));
        when(summaryProvider.summarize(PROJECT_ID)).thenReturn(null);

        withProvider().handle(groupCtx("/status"));

        assertThat(capturedReplyText()).containsIgnoringCase("not yet enabled");
    }

    @Test
    void handleHappyPathRendersAllThreeValues() {
        TelegramProjectLink link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, OWNER_ID, "My Group");
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));
        Instant lastActivity = Instant.parse("2026-04-21T10:15:30Z");
        when(summaryProvider.summarize(PROJECT_ID))
                .thenReturn(new ProjectPipelineSummary(3, lastActivity, new BigDecimal("12.5")));

        withProvider().handle(groupCtx("/status"));

        String reply = capturedReplyText();
        assertThat(reply)
                .contains("Active sparks: 3")
                .contains("2026-04-21T10:15:30Z")
                .contains("$12.50");
    }

    @Test
    void handleNullLastActivityRendersDash() {
        TelegramProjectLink link = TelegramProjectLink.create(PROJECT_ID, CHAT_ID, OWNER_ID, "My Group");
        when(projectRepo.findByChatIdAndIsActiveTrue(CHAT_ID)).thenReturn(Optional.of(link));
        when(summaryProvider.summarize(PROJECT_ID))
                .thenReturn(new ProjectPipelineSummary(0, null, BigDecimal.ZERO));

        withProvider().handle(groupCtx("/status"));

        String reply = capturedReplyText();
        assertThat(reply)
                .contains("Active sparks: 0")
                .contains("Last activity: —")
                .contains("$0.00");
    }
}
