package io.tacticl.business.telegram;

import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.identity.TelegramUsernameCache;
import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.TelegramCommandRouter;
import io.tacticl.business.telegram.spark.TelegramSparkInitiator;
import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.config.TelegramConfig;
import io.tacticl.client.telegram.dto.Chat;
import io.tacticl.client.telegram.dto.Message;
import io.tacticl.client.telegram.dto.MessageEntity;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.client.telegram.dto.Update;
import io.tacticl.client.telegram.dto.User;
import io.tacticl.data.telegram.entity.TelegramProjectLink;
import io.tacticl.data.telegram.repository.TelegramProjectLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramDispatchServiceTest {

    private static final String BOT_USERNAME = "tacticl_bot";

    private TelegramUserLinker linker;
    private TelegramBotClient bot;
    private TelegramCommandRouter commandRouter;
    private TelegramUsernameCache usernameCache;
    private TelegramSparkInitiator sparkInitiator;
    private TelegramProjectLinkRepository projectRepo;
    private TelegramIdentityResolver identity;
    private TelegramConfig telegramConfig;
    private TelegramDispatchService svc;

    @BeforeEach
    void setUp() {
        linker = mock(TelegramUserLinker.class);
        bot = mock(TelegramBotClient.class);
        commandRouter = mock(TelegramCommandRouter.class);
        usernameCache = mock(TelegramUsernameCache.class);
        sparkInitiator = mock(TelegramSparkInitiator.class);
        projectRepo = mock(TelegramProjectLinkRepository.class);
        identity = mock(TelegramIdentityResolver.class);
        telegramConfig = new TelegramConfig();
        telegramConfig.setBotUsername(BOT_USERNAME);

        svc = new TelegramDispatchService(linker, bot, commandRouter, usernameCache,
                sparkInitiator, projectRepo, identity, telegramConfig);
    }

    private static Message privateMsg(long chatId, long userId, String username, String text) {
        return new Message(1L, 0L,
                new Chat(chatId, "private", username, "First", null),
                new User(userId, false, username, "First"),
                text,
                null, null, null, null, null, null, null, false, null);
    }

    private static Message groupMsg(long chatId, long userId, String username, String text,
                                    List<MessageEntity> entities, Message replyTo) {
        return new Message(1L, 0L,
                new Chat(chatId, "group", null, null, "Group"),
                new User(userId, false, username, "First"),
                text,
                entities, null, null, null, null, null, null, false, replyTo);
    }

    // ---- Preserved /start linking flow tests ------------------------------

    @Test
    void handleStart_withValidToken_repliesSuccess() {
        when(linker.redeemToken("abc", 42L, "alice", "First"))
                .thenReturn(Optional.of("user-1"));

        svc.handle(new Update(1L, privateMsg(42L, 42L, "alice", "/start abc"), null, null, null, null));

        verify(bot).sendMessage(argThat(r ->
                r.chat_id() == 42L && r.text().contains("Linked")));
    }

    @Test
    void handleStart_withInvalidToken_repliesFailure() {
        when(linker.redeemToken(any(), anyLong(), any(), any()))
                .thenReturn(Optional.empty());

        svc.handle(new Update(1L, privateMsg(42L, 42L, "alice", "/start bad"), null, null, null, null));

        verify(bot).sendMessage(argThat(r ->
                r.text().toLowerCase().contains("invalid")
                        || r.text().toLowerCase().contains("expired")));
    }

    @Test
    void handleBareStart_repliesWelcome() {
        svc.handle(new Update(1L, privateMsg(42L, 42L, "alice", "/start"), null, null, null, null));
        verify(bot).sendMessage(argThat(r -> r.text().toLowerCase().contains("welcome")));
    }

    // ---- New wiring tests -------------------------------------------------

    @Test
    void slashCommandRoutedThroughCommandRouter() {
        when(commandRouter.dispatch(any())).thenReturn(true);

        Message msg = groupMsg(-100L, 42L, "alice", "/grant @bob runner", null, null);
        svc.handle(new Update(1L, msg, null, null, null, null));

        ArgumentCaptor<CommandContext> captor = ArgumentCaptor.forClass(CommandContext.class);
        verify(commandRouter).dispatch(captor.capture());
        CommandContext ctx = captor.getValue();
        assertThat(ctx.chatId()).isEqualTo(-100L);
        assertThat(ctx.telegramUserId()).isEqualTo(42L);
        assertThat(ctx.text()).isEqualTo("/grant @bob runner");
        assertThat(ctx.senderUsername()).isEqualTo("alice");
        assertThat(ctx.raw()).isSameAs(msg);
        verify(bot, never()).sendMessage(any());
    }

    @Test
    void unknownSlashCommandSilentlyDropped() {
        when(commandRouter.dispatch(any())).thenReturn(false);

        Message msg = groupMsg(-100L, 42L, "alice", "/nope", null, null);
        svc.handle(new Update(1L, msg, null, null, null, null));

        verify(commandRouter).dispatch(any());
        verify(bot, never()).sendMessage(any());
        verify(sparkInitiator, never()).initiate(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    void usernameCacheObservedOnInbound() {
        when(commandRouter.dispatch(any())).thenReturn(true);

        Message msg = groupMsg(-100L, 42L, "alice", "/members", null, null);
        svc.handle(new Update(1L, msg, null, null, null, null));

        verify(usernameCache).observe(-100L, 42L, "alice");
    }

    @Test
    void mentionInGroupTriggersSparkInitiator() {
        when(identity.resolveByChatId(42L)).thenReturn(Optional.of("user-alice"));
        TelegramProjectLink link = TelegramProjectLink.create("proj-1", -100L, "user-alice", "Group");
        when(projectRepo.findByChatIdAndIsActiveTrue(-100L)).thenReturn(Optional.of(link));

        String text = "@tacticl_bot deploy frontend";
        List<MessageEntity> entities = List.of(new MessageEntity("mention", 0, 12));
        Message msg = groupMsg(-100L, 42L, "alice", text, entities, null);

        svc.handle(new Update(1L, msg, null, null, null, null));

        verify(sparkInitiator).initiate(eq(-100L), eq("user-alice"),
                eq("deploy frontend"), eq(link), isNull());
    }

    @Test
    void replyToBotInGroupTriggersSparkInitiator() {
        when(identity.resolveByChatId(42L)).thenReturn(Optional.of("user-alice"));
        TelegramProjectLink link = TelegramProjectLink.create("proj-1", -100L, "user-alice", "Group");
        when(projectRepo.findByChatIdAndIsActiveTrue(-100L)).thenReturn(Optional.of(link));

        Message botReply = new Message(99L, 0L,
                new Chat(-100L, "group", null, null, "Group"),
                new User(7L, true, BOT_USERNAME, "Tacticl"),
                "Started",
                null, null, null, null, null, null, null, false, null);
        Message msg = groupMsg(-100L, 42L, "alice", "fix the build", null, botReply);

        svc.handle(new Update(1L, msg, null, null, null, null));

        verify(sparkInitiator).initiate(eq(-100L), eq("user-alice"),
                eq("fix the build"), eq(link), isNull());
    }

    @Test
    void mentionWithoutLinkRepliesPrompt() {
        when(identity.resolveByChatId(42L)).thenReturn(Optional.empty());

        String text = "@tacticl_bot deploy";
        List<MessageEntity> entities = List.of(new MessageEntity("mention", 0, 12));
        Message msg = groupMsg(-100L, 42L, "alice", text, entities, null);

        svc.handle(new Update(1L, msg, null, null, null, null));

        verify(bot).sendMessage(argThat(r ->
                r.chat_id() == -100L
                        && r.text().toLowerCase().contains("link your tacticl account")));
        verify(sparkInitiator, never()).initiate(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    void mentionWithoutProjectRepliesPrompt() {
        when(identity.resolveByChatId(42L)).thenReturn(Optional.of("user-alice"));
        when(projectRepo.findByChatIdAndIsActiveTrue(-100L)).thenReturn(Optional.empty());

        String text = "@tacticl_bot deploy";
        List<MessageEntity> entities = List.of(new MessageEntity("mention", 0, 12));
        Message msg = groupMsg(-100L, 42L, "alice", text, entities, null);

        svc.handle(new Update(1L, msg, null, null, null, null));

        verify(bot).sendMessage(argThat(r ->
                r.chat_id() == -100L
                        && r.text().toLowerCase().contains("no active project")
                        && r.text().contains("/init")));
        verify(sparkInitiator, never()).initiate(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    void plainTextNoMentionInGroupNoOp() {
        Message msg = groupMsg(-100L, 42L, "alice", "just chatting", null, null);
        svc.handle(new Update(1L, msg, null, null, null, null));

        verify(sparkInitiator, never()).initiate(anyLong(), anyString(), anyString(), any(), any());
        verify(bot, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void plainTextInDmRepliesFallback() {
        Message msg = privateMsg(42L, 42L, "alice", "hello there");
        svc.handle(new Update(1L, msg, null, null, null, null));

        verify(bot).sendMessage(any(SendMessageRequest.class));
        verify(sparkInitiator, never()).initiate(anyLong(), anyString(), anyString(), any(), any());
    }
}
