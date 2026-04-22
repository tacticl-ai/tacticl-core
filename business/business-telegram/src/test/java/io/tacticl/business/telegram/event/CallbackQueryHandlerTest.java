package io.tacticl.business.telegram.event;

import io.tacticl.business.telegram.identity.TelegramIdentityResolver;
import io.tacticl.business.telegram.permission.MemberPermissionService;
import io.tacticl.business.telegram.permission.PermissionCheck;
import io.tacticl.business.telegram.pipeline.TelegramCheckpointResolver;
import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.dto.CallbackQuery;
import io.tacticl.client.telegram.dto.Chat;
import io.tacticl.client.telegram.dto.Message;
import io.tacticl.client.telegram.dto.User;
import io.tacticl.data.pipeline.entity.CheckpointDecision;
import io.tacticl.data.telegram.entity.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CallbackQueryHandlerTest {

    private static final long CHAT_ID = -1001L;
    private static final long MESSAGE_ID = 42L;
    private static final long TELEGRAM_USER_ID = 7777L;
    private static final String TACTICL_USER_ID = "user-1";
    private static final String CHECKPOINT_ID = "cp-abc";
    private static final String CALLBACK_ID = "cb-1";

    private TelegramIdentityResolver identity;
    private MemberPermissionService permissions;
    private TelegramCheckpointResolver checkpointResolver;
    private TelegramBotClient bot;
    private CallbackQueryHandler handler;

    @BeforeEach
    void setUp() {
        identity = mock(TelegramIdentityResolver.class);
        permissions = mock(MemberPermissionService.class);
        checkpointResolver = mock(TelegramCheckpointResolver.class);
        bot = mock(TelegramBotClient.class);
        handler = new CallbackQueryHandler(identity, permissions, checkpointResolver, bot);
    }

    @Test
    void malformedCallbackDataRepliesInvalidActionAndSkipsResolve() {
        CallbackQuery cb = callback("not-a-cp-format");

        handler.handle(cb);

        verify(bot).answerCallbackQuery(CALLBACK_ID, "Invalid action");
        verifyNoInteractions(identity, permissions, checkpointResolver);
    }

    @Test
    void unknownActionReplysInvalidAction() {
        CallbackQuery cb = callback("cp:bogus:" + CHECKPOINT_ID);

        handler.handle(cb);

        verify(bot).answerCallbackQuery(CALLBACK_ID, "Invalid action");
        verifyNoInteractions(identity, permissions, checkpointResolver);
    }

    @Test
    void unlinkedUserReplysLinkYourAccount() {
        CallbackQuery cb = callback("cp:approve:" + CHECKPOINT_ID);
        when(identity.resolveByChatId(TELEGRAM_USER_ID)).thenReturn(Optional.empty());

        handler.handle(cb);

        verify(bot).answerCallbackQuery(CALLBACK_ID, "Link your Tacticl account first");
        verifyNoInteractions(permissions, checkpointResolver);
    }

    @Test
    void insufficientRoleReplysNeedRunnerPermission() {
        CallbackQuery cb = callback("cp:approve:" + CHECKPOINT_ID);
        when(identity.resolveByChatId(TELEGRAM_USER_ID)).thenReturn(Optional.of(TACTICL_USER_ID));
        when(permissions.require(CHAT_ID, TACTICL_USER_ID, MemberRole.RUNNER))
            .thenReturn(PermissionCheck.deny(MemberRole.OBSERVER, MemberRole.RUNNER, "too low"));

        handler.handle(cb);

        verify(bot).answerCallbackQuery(CALLBACK_ID, "Need runner permission");
        verifyNoInteractions(checkpointResolver);
        verify(bot, never()).editMessageText(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void happyPathInvokesResolverAnswersAndStripsKeyboard() {
        CallbackQuery cb = callback("cp:approve:" + CHECKPOINT_ID);
        when(identity.resolveByChatId(TELEGRAM_USER_ID)).thenReturn(Optional.of(TACTICL_USER_ID));
        when(permissions.require(CHAT_ID, TACTICL_USER_ID, MemberRole.RUNNER))
            .thenReturn(PermissionCheck.allow(MemberRole.RUNNER));
        when(checkpointResolver.resolve(TACTICL_USER_ID, CHECKPOINT_ID, "approve"))
            .thenReturn(TelegramCheckpointResolver.Result.success(CheckpointDecision.APPROVED, "spark-1"));

        handler.handle(cb);

        verify(checkpointResolver).resolve(TACTICL_USER_ID, CHECKPOINT_ID, "approve");

        ArgumentCaptor<String> toast = ArgumentCaptor.forClass(String.class);
        verify(bot).answerCallbackQuery(eq(CALLBACK_ID), toast.capture());
        assertThat(toast.getValue()).contains("Approved");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        // Null markup strips the inline keyboard so the same checkpoint cannot be re-tapped.
        verify(bot).editMessageText(eq(CHAT_ID), eq(MESSAGE_ID), body.capture(), isNull());
        assertThat(body.getValue()).contains("approved");
    }

    @Test
    void changesActionSurfacesReworkToast() {
        CallbackQuery cb = callback("cp:changes:" + CHECKPOINT_ID);
        when(identity.resolveByChatId(TELEGRAM_USER_ID)).thenReturn(Optional.of(TACTICL_USER_ID));
        when(permissions.require(CHAT_ID, TACTICL_USER_ID, MemberRole.RUNNER))
            .thenReturn(PermissionCheck.allow(MemberRole.OWNER));
        when(checkpointResolver.resolve(TACTICL_USER_ID, CHECKPOINT_ID, "changes"))
            .thenReturn(TelegramCheckpointResolver.Result.success(CheckpointDecision.REWORK, "spark-1"));

        handler.handle(cb);

        ArgumentCaptor<String> toast = ArgumentCaptor.forClass(String.class);
        verify(bot).answerCallbackQuery(eq(CALLBACK_ID), toast.capture());
        assertThat(toast.getValue()).containsIgnoringCase("Changes");
    }

    @Test
    void unknownCheckpointDoesNotStripKeyboard() {
        CallbackQuery cb = callback("cp:approve:" + CHECKPOINT_ID);
        when(identity.resolveByChatId(TELEGRAM_USER_ID)).thenReturn(Optional.of(TACTICL_USER_ID));
        when(permissions.require(CHAT_ID, TACTICL_USER_ID, MemberRole.RUNNER))
            .thenReturn(PermissionCheck.allow(MemberRole.RUNNER));
        when(checkpointResolver.resolve(TACTICL_USER_ID, CHECKPOINT_ID, "approve"))
            .thenReturn(TelegramCheckpointResolver.Result.failure(
                TelegramCheckpointResolver.ResultCode.NOT_FOUND, "Checkpoint no longer pending"));

        handler.handle(cb);

        verify(bot).answerCallbackQuery(CALLBACK_ID, "Checkpoint no longer pending");
        verify(bot, never()).editMessageText(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void nullCallbackIsIgnored() {
        handler.handle(null);
        verifyNoInteractions(bot, identity, permissions, checkpointResolver);
    }

    private CallbackQuery callback(String data) {
        User from = new User(TELEGRAM_USER_ID, false, "alice", "Alice");
        Chat chat = new Chat(CHAT_ID, "supergroup", null, null, "Test group");
        Message message = new Message(
            MESSAGE_ID, 0L, chat, null, "checkpoint body",
            null, null, null, null, null, null, null, null, null
        );
        return new CallbackQuery(CALLBACK_ID, from, message, data);
    }
}
