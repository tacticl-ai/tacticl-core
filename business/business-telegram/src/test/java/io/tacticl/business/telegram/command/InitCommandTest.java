package io.tacticl.business.telegram.command;

import io.cidadel.framework.exception.CidadelException;
import io.tacticl.business.telegram.audit.TelegramAuditLogger;
import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.outbound.OutboundMessage;
import io.tacticl.business.telegram.outbound.TelegramOutboundQueue;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.dto.Chat;
import io.tacticl.client.telegram.dto.ForumTopic;
import io.tacticl.client.telegram.dto.Message;
import io.tacticl.client.telegram.exception.TelegramErrorDetails;
import io.tacticl.data.pipeline.entity.PdlcRole;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class InitCommandTest {

    private TelegramIdentityResolver identity;
    private TelegramProjectLinkRepository projectRepo;
    private TelegramOutboundQueue outbound;
    private TelegramBotClient bot;
    private TelegramAuditLogger auditLogger;
    private InitCommand command;

    @BeforeEach
    void setUp() {
        identity = mock(TelegramIdentityResolver.class);
        projectRepo = mock(TelegramProjectLinkRepository.class);
        outbound = mock(TelegramOutboundQueue.class);
        bot = mock(TelegramBotClient.class);
        auditLogger = mock(TelegramAuditLogger.class);
        command = new InitCommand(identity, projectRepo, outbound, bot, auditLogger);
    }

    private static CommandContext groupCtx(long chatId,
                                           long telegramUserId,
                                           String senderUsername,
                                           String groupTitle,
                                           String text) {
        Chat chat = new Chat(chatId, "group", null, null, groupTitle, false);
        Message msg = new Message(
                1L, 0L, chat, null, text,
                null, null, null, null,
                null, null, null, false, null);
        return new CommandContext(chatId, telegramUserId, text, senderUsername, msg);
    }

    private static CommandContext forumCtx(long chatId,
                                           long telegramUserId,
                                           String senderUsername,
                                           String groupTitle,
                                           String text) {
        Chat chat = new Chat(chatId, "supergroup", null, null, groupTitle, true);
        Message msg = new Message(
                1L, 0L, chat, null, text,
                null, null, null, null,
                null, null, null, false, null);
        return new CommandContext(chatId, telegramUserId, text, senderUsername, msg);
    }

    private static CommandContext supergroupCtx(long chatId,
                                                long telegramUserId,
                                                String senderUsername,
                                                String groupTitle,
                                                String text,
                                                boolean isForum) {
        Chat chat = new Chat(chatId, "supergroup", null, null, groupTitle, isForum);
        Message msg = new Message(
                1L, 0L, chat, null, text,
                null, null, null, null,
                null, null, null, false, null);
        return new CommandContext(chatId, telegramUserId, text, senderUsername, msg);
    }

    private static CommandContext regularGroupForumFlagCtx(long chatId,
                                                           long telegramUserId,
                                                           String senderUsername,
                                                           String groupTitle,
                                                           String text) {
        // Defensive case: Telegram should never send is_forum=true on a non-supergroup,
        // but the gate must still skip topic creation.
        Chat chat = new Chat(chatId, "group", null, null, groupTitle, true);
        Message msg = new Message(
                1L, 0L, chat, null, text,
                null, null, null, null,
                null, null, null, false, null);
        return new CommandContext(chatId, telegramUserId, text, senderUsername, msg);
    }

    @Test
    void handleUnlinkedSenderDmsLinkPrompt() {
        long chatId = -100L;
        long telegramUserId = 99L;
        when(identity.resolveByChatId(telegramUserId)).thenReturn(Optional.empty());

        command.handle(groupCtx(chatId, telegramUserId, "alice", "My Group", "/init"));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(telegramUserId), captor.capture());
        assertThat(captor.getValue().request().chat_id()).isEqualTo(telegramUserId);
        assertThat(captor.getValue().request().text()).contains("Link your Tacticl account");
        verify(projectRepo, never()).save(any());
        verify(projectRepo, never()).findByChatIdAndIsActiveTrue(anyLong());

        // WHY: forensic trail captures pre-link rejections too — payload encodes the reason.
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogger).record(eq(chatId), eq(telegramUserId), eq((String) null),
                eq("INIT"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).contains("unlinked_sender");
    }

    @Test
    void handleAlreadyClaimedGroupRepliesInGroup() {
        long chatId = -100L;
        long telegramUserId = 99L;
        when(identity.resolveByChatId(telegramUserId)).thenReturn(Optional.of("user-abc"));
        TelegramProjectLink existing = TelegramProjectLink.create(
                "proj-123", chatId, "user-abc", "My Group");
        when(projectRepo.findByChatIdAndIsActiveTrue(chatId)).thenReturn(Optional.of(existing));

        command.handle(groupCtx(chatId, telegramUserId, "alice", "My Group", "/init"));

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(chatId), captor.capture());
        assertThat(captor.getValue().request().chat_id()).isEqualTo(chatId);
        assertThat(captor.getValue().request().text())
                .contains("Already linked")
                .contains("proj-123");
        verify(projectRepo, never()).save(any());
        // WHY: re-running /init on a claimed group must NOT touch the bot. Locks in
        // the contract that already-linked groups never trigger forum-topic creation.
        verify(bot, never()).createForumTopic(anyLong(), anyString());

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogger).record(eq(chatId), eq(telegramUserId), eq("user-abc"),
                eq("INIT"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .contains("already_linked")
                .contains("proj-123");
    }

    @Test
    void handleHappyPathPersistsLinkAndWelcomes() {
        long chatId = -100L;
        long telegramUserId = 99L;
        String tacticlUserId = "user-abc";
        String groupTitle = "My Group";
        when(identity.resolveByChatId(telegramUserId)).thenReturn(Optional.of(tacticlUserId));
        when(projectRepo.findByChatIdAndIsActiveTrue(chatId)).thenReturn(Optional.empty());

        command.handle(groupCtx(chatId, telegramUserId, "alice", groupTitle, "/init"));

        ArgumentCaptor<TelegramProjectLink> linkCaptor =
                ArgumentCaptor.forClass(TelegramProjectLink.class);
        verify(projectRepo).save(linkCaptor.capture());
        TelegramProjectLink saved = linkCaptor.getValue();
        assertThat(saved.getOwnerUserId()).isEqualTo(tacticlUserId);
        assertThat(saved.getChatId()).isEqualTo(chatId);
        assertThat(saved.getGroupTitle()).isEqualTo(groupTitle);
        assertThat(saved.getProjectId()).isNotBlank();

        ArgumentCaptor<OutboundMessage> msgCaptor =
                ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(chatId), msgCaptor.capture());
        String welcome = msgCaptor.getValue().request().text();
        assertThat(welcome)
                .contains("@alice")
                .contains(tacticlUserId)
                .contains("/grant")
                .contains("/revoke")
                .contains("/transfer")
                .contains("/members")
                .contains("/status")
                .contains("/help");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogger).record(eq(chatId), eq(telegramUserId), eq(tacticlUserId),
                eq("INIT"), payloadCaptor.capture());
        // Happy-path payload carries the new projectId; project ids are UUIDs so we just
        // assert the field name is present.
        assertThat(payloadCaptor.getValue()).contains("projectId");
    }

    @Test
    void handleForumGroupCreatesTopicsAndPersists() {
        long chatId = -1001L;
        long telegramUserId = 99L;
        String tacticlUserId = "user-abc";
        when(identity.resolveByChatId(telegramUserId)).thenReturn(Optional.of(tacticlUserId));
        when(projectRepo.findByChatIdAndIsActiveTrue(chatId)).thenReturn(Optional.empty());

        AtomicInteger threadIdSeq = new AtomicInteger(1000);
        when(bot.createForumTopic(eq(chatId), anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(1);
            return new ForumTopic(threadIdSeq.getAndIncrement(), name, null, null);
        });

        command.handle(forumCtx(chatId, telegramUserId, "alice", "Forum Group", "/init"));

        // 12 PdlcRoles → 12 createForumTopic invocations
        verify(bot, times(12)).createForumTopic(eq(chatId), anyString());
        for (PdlcRole role : PdlcRole.values()) {
            verify(bot).createForumTopic(chatId, role.name());
        }

        ArgumentCaptor<TelegramProjectLink> linkCaptor =
                ArgumentCaptor.forClass(TelegramProjectLink.class);
        verify(projectRepo, times(2)).save(linkCaptor.capture());
        List<TelegramProjectLink> saves = linkCaptor.getAllValues();

        TelegramProjectLink finalSave = saves.get(1);
        Map<PdlcRole, Long> topics = finalSave.getForumTopics();
        assertThat(topics).isNotNull().hasSize(12);
        long expectedThreadId = 1000L;
        for (PdlcRole role : PdlcRole.values()) {
            assertThat(topics).containsEntry(role, expectedThreadId);
            expectedThreadId++;
        }

        // Welcome still enqueued
        ArgumentCaptor<OutboundMessage> msgCaptor =
                ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(chatId), msgCaptor.capture());
        assertThat(msgCaptor.getValue().request().text()).contains("@alice");
    }

    @Test
    void handleNonForumSupergroupSkipsTopics() {
        long chatId = -1002L;
        long telegramUserId = 99L;
        String tacticlUserId = "user-abc";
        when(identity.resolveByChatId(telegramUserId)).thenReturn(Optional.of(tacticlUserId));
        when(projectRepo.findByChatIdAndIsActiveTrue(chatId)).thenReturn(Optional.empty());

        command.handle(supergroupCtx(chatId, telegramUserId, "alice", "Plain SG", "/init", false));

        verify(bot, never()).createForumTopic(anyLong(), anyString());
        verify(projectRepo, times(1)).save(any());

        ArgumentCaptor<OutboundMessage> msgCaptor =
                ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(chatId), msgCaptor.capture());
        assertThat(msgCaptor.getValue().request().text()).contains("@alice");
    }

    @Test
    void handleRegularGroupSkipsTopics() {
        long chatId = -1003L;
        long telegramUserId = 99L;
        String tacticlUserId = "user-abc";
        when(identity.resolveByChatId(telegramUserId)).thenReturn(Optional.of(tacticlUserId));
        when(projectRepo.findByChatIdAndIsActiveTrue(chatId)).thenReturn(Optional.empty());

        command.handle(regularGroupForumFlagCtx(chatId, telegramUserId, "alice", "Regular", "/init"));

        verify(bot, never()).createForumTopic(anyLong(), anyString());
        verify(projectRepo, times(1)).save(any());
    }

    @Test
    void handleForumCreationFailureGracefullyFallsBack() {
        long chatId = -1004L;
        long telegramUserId = 99L;
        String tacticlUserId = "user-abc";
        when(identity.resolveByChatId(telegramUserId)).thenReturn(Optional.of(tacticlUserId));
        when(projectRepo.findByChatIdAndIsActiveTrue(chatId)).thenReturn(Optional.empty());

        when(bot.createForumTopic(eq(chatId), anyString())).thenThrow(
                new CidadelException(TelegramErrorDetails.BOT_API_ERROR, "client-telegram", "CHAT_NOT_FORUM"));

        command.handle(forumCtx(chatId, telegramUserId, "alice", "Forum Group", "/init"));

        verify(bot, times(1)).createForumTopic(eq(chatId), anyString());
        verify(projectRepo, times(1)).save(any());

        ArgumentCaptor<OutboundMessage> msgCaptor =
                ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(chatId), msgCaptor.capture());
        assertThat(msgCaptor.getValue().request().text()).contains("@alice");
    }

    @Test
    void handlePartialForumCreationPersistsWhatSucceeded() {
        long chatId = -1005L;
        long telegramUserId = 99L;
        String tacticlUserId = "user-abc";
        when(identity.resolveByChatId(telegramUserId)).thenReturn(Optional.of(tacticlUserId));
        when(projectRepo.findByChatIdAndIsActiveTrue(chatId)).thenReturn(Optional.empty());

        AtomicInteger calls = new AtomicInteger();
        AtomicInteger threadIdSeq = new AtomicInteger(2000);
        when(bot.createForumTopic(eq(chatId), anyString())).thenAnswer(inv -> {
            int n = calls.incrementAndGet();
            if (n > 3) {
                throw new CidadelException(TelegramErrorDetails.BOT_API_ERROR,
                        "client-telegram", "CHAT_NOT_FORUM");
            }
            String name = inv.getArgument(1);
            return new ForumTopic(threadIdSeq.getAndIncrement(), name, null, null);
        });

        command.handle(forumCtx(chatId, telegramUserId, "alice", "Forum Group", "/init"));

        verify(bot, times(4)).createForumTopic(eq(chatId), anyString());

        ArgumentCaptor<TelegramProjectLink> linkCaptor =
                ArgumentCaptor.forClass(TelegramProjectLink.class);
        verify(projectRepo, times(2)).save(linkCaptor.capture());
        List<TelegramProjectLink> saves = linkCaptor.getAllValues();
        Map<PdlcRole, Long> topics = saves.get(1).getForumTopics();
        assertThat(topics).isNotNull().hasSize(3);
        assertThat(topics).containsOnlyKeys(PdlcRole.PO, PdlcRole.RESEARCHER, PdlcRole.ARCHITECT);
        assertThat(topics.get(PdlcRole.PO)).isEqualTo(2000L);
        assertThat(topics.get(PdlcRole.RESEARCHER)).isEqualTo(2001L);
        assertThat(topics.get(PdlcRole.ARCHITECT)).isEqualTo(2002L);

        ArgumentCaptor<OutboundMessage> msgCaptor =
                ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outbound).enqueue(eq(chatId), msgCaptor.capture());
        assertThat(msgCaptor.getValue().request().text()).contains("@alice");
    }
}
