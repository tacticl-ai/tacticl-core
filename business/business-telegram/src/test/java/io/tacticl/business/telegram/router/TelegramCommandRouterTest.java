package io.tacticl.business.telegram.router;

import io.tacticl.client.telegram.dto.Chat;
import io.tacticl.client.telegram.dto.Message;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramCommandRouterTest {

    private static Message groupMsg(long chatId, String text) {
        return new Message(1L, 0L,
                new Chat(chatId, "group", null, null, "Test Group", false),
                null, text,
                null, null, null, null, null, null, null, false, null);
    }

    private static Message dmMsg(long chatId, String text) {
        return new Message(1L, 0L,
                new Chat(chatId, "private", "alice", "Alice", null, false),
                null, text,
                null, null, null, null, null, null, null, false, null);
    }

    private static CommandHandler handler(String name, CommandHandler.Scope scope, Runnable onHandle) {
        return new CommandHandler() {
            @Override public String commandName() { return name; }
            @Override public CommandHandler.Scope scope() { return scope; }
            @Override public void handle(CommandContext ctx) { onHandle.run(); }
        };
    }

    @Test
    void routesToMatchingGroupHandler() {
        AtomicInteger calls = new AtomicInteger();
        var h = handler("/init", CommandHandler.Scope.GROUP, calls::incrementAndGet);
        var router = new TelegramCommandRouter(List.of(h));

        var ctx = new CommandContext(-100L, 42L, "/init", "alice", groupMsg(-100L, "/init"));
        boolean dispatched = router.dispatch(ctx);

        assertThat(dispatched).isTrue();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void dmScopeIgnoredInGroup() {
        AtomicInteger calls = new AtomicInteger();
        var h = handler("/link", CommandHandler.Scope.DM, calls::incrementAndGet);
        var router = new TelegramCommandRouter(List.of(h));

        var ctx = new CommandContext(-100L, 42L, "/link", "alice", groupMsg(-100L, "/link"));
        boolean dispatched = router.dispatch(ctx);

        assertThat(dispatched).isFalse();
        assertThat(calls.get()).isZero();
    }

    @Test
    void anyScopeMatchesBothGroupAndDm() {
        AtomicInteger calls = new AtomicInteger();
        var h = handler("/help", CommandHandler.Scope.ANY, calls::incrementAndGet);
        var router = new TelegramCommandRouter(List.of(h));

        var groupCtx = new CommandContext(-100L, 42L, "/help", "alice", groupMsg(-100L, "/help"));
        var dmCtx = new CommandContext(42L, 42L, "/help", "alice", dmMsg(42L, "/help"));

        assertThat(router.dispatch(groupCtx)).isTrue();
        assertThat(router.dispatch(dmCtx)).isTrue();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void handlerExceptionSwallowedAndLogged() {
        var h = handler("/boom", CommandHandler.Scope.ANY, () -> {
            throw new RuntimeException("kaboom");
        });
        var router = new TelegramCommandRouter(List.of(h));

        var ctx = new CommandContext(42L, 42L, "/boom", "alice", dmMsg(42L, "/boom"));
        boolean dispatched = router.dispatch(ctx);

        // Handler matched (scope ANY), so dispatch reports true even though handler threw.
        assertThat(dispatched).isTrue();
    }

    @Test
    void stripBotMentionFromCommand() {
        AtomicReference<CommandContext> received = new AtomicReference<>();
        var h = new CommandHandler() {
            @Override public String commandName() { return "/init"; }
            @Override public CommandHandler.Scope scope() { return CommandHandler.Scope.GROUP; }
            @Override public void handle(CommandContext ctx) { received.set(ctx); }
        };
        var router = new TelegramCommandRouter(List.of(h));

        var ctx = new CommandContext(-100L, 42L, "/init@tacticl_bot foo", "alice",
                groupMsg(-100L, "/init@tacticl_bot foo"));
        boolean dispatched = router.dispatch(ctx);

        assertThat(dispatched).isTrue();
        assertThat(received.get()).isSameAs(ctx);
        assertThat(received.get().text()).isEqualTo("/init@tacticl_bot foo");
    }

    @Test
    void unknownCommandReturnsFalse() {
        AtomicInteger calls = new AtomicInteger();
        var h = handler("/init", CommandHandler.Scope.ANY, calls::incrementAndGet);
        var router = new TelegramCommandRouter(List.of(h));

        var ctx = new CommandContext(-100L, 42L, "/nope", "alice", groupMsg(-100L, "/nope"));
        boolean dispatched = router.dispatch(ctx);

        assertThat(dispatched).isFalse();
        assertThat(calls.get()).isZero();
    }
}
