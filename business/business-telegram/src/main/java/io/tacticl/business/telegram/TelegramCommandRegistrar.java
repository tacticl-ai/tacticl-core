package io.tacticl.business.telegram;

import io.tacticl.business.telegram.router.CommandHandler;
import io.tacticl.client.telegram.TelegramBotClient;
import io.tacticl.client.telegram.dto.BotCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Publishes the bot's slash-command catalog to Telegram on application startup
 * via {@code /setMyCommands}. Without this, the Telegram client shows no
 * autocomplete commands — required for self-service onboarding.
 *
 * <p>Two scope-bucketed calls are made (BotFather convention):
 * <ul>
 *   <li>{@code all_group_chats} — handlers with {@link CommandHandler.Scope#GROUP}
 *       or {@link CommandHandler.Scope#ANY}</li>
 *   <li>{@code all_private_chats} — handlers with {@link CommandHandler.Scope#DM}
 *       or {@link CommandHandler.Scope#ANY}</li>
 * </ul>
 * ANY-scoped handlers intentionally appear in both lists.
 *
 * <p>Each {@code setMyCommands} call is guarded — a Telegram API failure on
 * startup must not bring the application down (parity with
 * {@link TelegramWebhookRegistrar}).
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramCommandRegistrar {

    private static final Logger logger = LoggerFactory.getLogger(TelegramCommandRegistrar.class);

    private final TelegramBotClient bot;
    private final List<CommandHandler> handlers;

    public TelegramCommandRegistrar(TelegramBotClient bot, List<CommandHandler> handlers) {
        this.bot = bot;
        this.handlers = handlers;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void registerOnStartup() {
        List<BotCommand> groupCommands = new ArrayList<>();
        List<BotCommand> dmCommands = new ArrayList<>();
        for (CommandHandler handler : handlers) {
            BotCommand cmd = toBotCommand(handler);
            CommandHandler.Scope scope = handler.scope();
            if (scope == CommandHandler.Scope.GROUP || scope == CommandHandler.Scope.ANY) {
                groupCommands.add(cmd);
            }
            if (scope == CommandHandler.Scope.DM || scope == CommandHandler.Scope.ANY) {
                dmCommands.add(cmd);
            }
        }

        // WHY: skip empty lists — Telegram returns ok=true for empty arrays but
        // it's a wasted API call and an empty publication clears any previously
        // registered commands at that scope.
        if (!groupCommands.isEmpty()) {
            publish(groupCommands, "all_group_chats");
        }
        if (!dmCommands.isEmpty()) {
            publish(dmCommands, "all_private_chats");
        }
    }

    private void publish(List<BotCommand> commands, String scopeType) {
        try {
            boolean ok = bot.setMyCommands(commands, scopeType);
            if (ok) {
                logger.info("Telegram setMyCommands published {} command(s) at scope {}",
                        commands.size(), scopeType);
            } else {
                logger.warn("Telegram setMyCommands returned ok=false for scope {}", scopeType);
            }
        } catch (Exception e) {
            // WHY: failure to publish the command catalog must never prevent boot —
            // Telegram outage / token issue would brick the entire app otherwise.
            logger.warn("Telegram setMyCommands failed for scope {} — continuing", scopeType, e);
        }
    }

    private static BotCommand toBotCommand(CommandHandler handler) {
        String name = handler.commandName();
        // WHY: Telegram's setMyCommands rejects names with leading slash.
        String stripped = name.startsWith("/") ? name.substring(1) : name;
        return new BotCommand(stripped, handler.description());
    }
}
