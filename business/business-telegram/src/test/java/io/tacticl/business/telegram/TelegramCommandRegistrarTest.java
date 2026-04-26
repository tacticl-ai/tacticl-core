package io.tacticl.business.telegram;

import io.tacticl.business.telegram.router.CommandContext;
import io.tacticl.business.telegram.router.CommandHandler;
import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.dto.BotCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramCommandRegistrarTest {

    private TelegramBotClient bot;

    @BeforeEach
    void setUp() {
        bot = mock(TelegramBotClient.class);
        when(bot.setMyCommands(any(), any())).thenReturn(true);
    }

    @Test
    void onContextRefreshed_publishesGroupCommands() {
        CommandHandler init = stub("/init", CommandHandler.Scope.GROUP, "Claim group");
        CommandHandler help = stub("/help", CommandHandler.Scope.GROUP, "Show commands");
        TelegramCommandRegistrar registrar =
                new TelegramCommandRegistrar(bot, List.of(init, help));

        registrar.registerOnStartup();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BotCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(bot).setMyCommands(captor.capture(), eq("all_group_chats"));
        assertThat(captor.getValue())
                .extracting(BotCommand::command)
                .containsExactlyInAnyOrder("init", "help");
    }

    @Test
    void onContextRefreshed_publishesDmCommands() {
        CommandHandler dmOnly = stub("/dm", CommandHandler.Scope.DM, "DM only");
        CommandHandler anyScope = stub("/both", CommandHandler.Scope.ANY, "Both scopes");
        CommandHandler groupOnly = stub("/grp", CommandHandler.Scope.GROUP, "Group only");
        TelegramCommandRegistrar registrar =
                new TelegramCommandRegistrar(bot, List.of(dmOnly, anyScope, groupOnly));

        registrar.registerOnStartup();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BotCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(bot).setMyCommands(captor.capture(), eq("all_private_chats"));
        // ANY-scoped handlers must appear in BOTH lists; group-only must NOT appear in DM list.
        assertThat(captor.getValue())
                .extracting(BotCommand::command)
                .containsExactlyInAnyOrder("dm", "both");
    }

    @Test
    void onContextRefreshed_anyScopedHandler_appearsInGroupList() {
        CommandHandler anyScope = stub("/both", CommandHandler.Scope.ANY, "Both scopes");
        TelegramCommandRegistrar registrar =
                new TelegramCommandRegistrar(bot, List.of(anyScope));

        registrar.registerOnStartup();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BotCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(bot).setMyCommands(captor.capture(), eq("all_group_chats"));
        assertThat(captor.getValue())
                .extracting(BotCommand::command)
                .containsExactly("both");
    }

    @Test
    void setMyCommandsThrows_swallowsException_appStillBoots() {
        CommandHandler init = stub("/init", CommandHandler.Scope.GROUP, "Claim group");
        CommandHandler dm = stub("/dm", CommandHandler.Scope.DM, "DM only");
        // WHY: if the GROUP call blows up, the DM call must still run — guard each call independently.
        doThrow(new RuntimeException("Telegram API down"))
                .when(bot).setMyCommands(any(), eq("all_group_chats"));
        TelegramCommandRegistrar registrar =
                new TelegramCommandRegistrar(bot, List.of(init, dm));

        // Must not rethrow.
        registrar.registerOnStartup();

        verify(bot).setMyCommands(any(), eq("all_group_chats"));
        verify(bot).setMyCommands(any(), eq("all_private_chats"));
    }

    @Test
    void botCommandStripsLeadingSlash() {
        CommandHandler init = stub("/init", CommandHandler.Scope.GROUP, "Claim group");
        TelegramCommandRegistrar registrar =
                new TelegramCommandRegistrar(bot, List.of(init));

        registrar.registerOnStartup();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BotCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(bot).setMyCommands(captor.capture(), eq("all_group_chats"));
        BotCommand only = captor.getValue().get(0);
        assertThat(only.command()).isEqualTo("init");
        assertThat(only.description()).isEqualTo("Claim group");
    }

    @Test
    void emptyDmList_skipsDmRegistration() {
        // All real handlers are GROUP-scoped today; ensure we don't make a wasted API call
        // with an empty command list.
        CommandHandler init = stub("/init", CommandHandler.Scope.GROUP, "Claim group");
        TelegramCommandRegistrar registrar =
                new TelegramCommandRegistrar(bot, List.of(init));

        registrar.registerOnStartup();

        verify(bot).setMyCommands(any(), eq("all_group_chats"));
        verify(bot, never()).setMyCommands(any(), eq("all_private_chats"));
    }

    private static CommandHandler stub(String name, CommandHandler.Scope scope, String description) {
        return new CommandHandler() {
            @Override
            public String commandName() { return name; }
            @Override
            public Scope scope() { return scope; }
            @Override
            public String description() { return description; }
            @Override
            public void handle(CommandContext ctx) { /* no-op */ }
        };
    }
}
