package io.tacticl.business.telegram;

import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.dto.Chat;
import io.tacticl.client.telegram.dto.Message;
import io.tacticl.client.telegram.dto.SendMessageRequest;
import io.tacticl.client.telegram.dto.Update;
import io.tacticl.client.telegram.dto.User;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramDispatchServiceTest {

    @Test
    void handleStart_withValidToken_repliesSuccess() {
        TelegramUserLinker linker = mock(TelegramUserLinker.class);
        TelegramBotClient bot = mock(TelegramBotClient.class);
        when(linker.redeemToken("abc", 42L, "alice", "Alice"))
                .thenReturn(Optional.of("user-1"));

        TelegramDispatchService svc = new TelegramDispatchService(linker, bot);
        Message msg = new Message(1L, 0L,
                new Chat(42L, "private", "alice", "Alice"),
                new User(42L, false, "alice", "Alice"),
                "/start abc",
                null, null, null, null, null, null, null, false, null);

        svc.handle(new Update(1L, msg, null));

        verify(bot).sendMessage(argThat(r ->
                r.chat_id() == 42L && r.text().contains("Linked")));
    }

    @Test
    void handleStart_withInvalidToken_repliesFailure() {
        TelegramUserLinker linker = mock(TelegramUserLinker.class);
        TelegramBotClient bot = mock(TelegramBotClient.class);
        when(linker.redeemToken(any(), anyLong(), any(), any()))
                .thenReturn(Optional.empty());

        TelegramDispatchService svc = new TelegramDispatchService(linker, bot);
        Message msg = new Message(1L, 0L,
                new Chat(42L, "private", "alice", "Alice"),
                new User(42L, false, "alice", "Alice"),
                "/start bad",
                null, null, null, null, null, null, null, false, null);

        svc.handle(new Update(1L, msg, null));

        verify(bot).sendMessage(argThat(r ->
                r.text().toLowerCase().contains("invalid")
                        || r.text().toLowerCase().contains("expired")));
    }

    @Test
    void handleBareStart_repliesWelcome() {
        TelegramUserLinker linker = mock(TelegramUserLinker.class);
        TelegramBotClient bot = mock(TelegramBotClient.class);
        TelegramDispatchService svc = new TelegramDispatchService(linker, bot);
        Message msg = new Message(1L, 0L,
                new Chat(42L, "private", "alice", "Alice"),
                new User(42L, false, "alice", "Alice"),
                "/start",
                null, null, null, null, null, null, null, false, null);
        svc.handle(new Update(1L, msg, null));
        verify(bot).sendMessage(argThat(r -> r.text().toLowerCase().contains("welcome")));
    }

    @Test
    void handleUnknownCommand_repliesNotSupported() {
        TelegramUserLinker linker = mock(TelegramUserLinker.class);
        TelegramBotClient bot = mock(TelegramBotClient.class);
        TelegramDispatchService svc = new TelegramDispatchService(linker, bot);
        Message msg = new Message(1L, 0L,
                new Chat(42L, "private", "alice", "Alice"),
                new User(42L, false, "alice", "Alice"),
                "hello there",
                null, null, null, null, null, null, null, false, null);
        svc.handle(new Update(1L, msg, null));
        verify(bot).sendMessage(any(SendMessageRequest.class));
    }
}
