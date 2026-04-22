package io.tacticl.business.telegram.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dispatches Telegram commands to the first {@link CommandHandler} whose
 * {@link CommandHandler#commandName() name} and {@link CommandHandler.Scope scope}
 * match the incoming {@link CommandContext}.
 *
 * <p>Handler failures are logged and swallowed — a single bad command must not
 * crash the dispatch loop for subsequent updates.
 */
@Component
@ConditionalOnProperty(name = "tacticl.telegram.enabled", havingValue = "true")
public class TelegramCommandRouter {

    private static final Logger logger = LoggerFactory.getLogger(TelegramCommandRouter.class);

    private final Map<String, List<CommandHandler>> handlers;

    public TelegramCommandRouter(List<CommandHandler> all) {
        this.handlers = all.stream().collect(Collectors.groupingBy(CommandHandler::commandName));
    }

    /**
     * Route the given context to a matching handler.
     *
     * @return {@code true} if a handler matched (regardless of whether the
     *         handler itself threw); {@code false} if no handler was registered
     *         for the token, or none matched the chat scope.
     */
    public boolean dispatch(CommandContext ctx) {
        String first = ctx.text().split("\\s+", 2)[0];
        String token = first.split("@", 2)[0]; // strip @botname mention
        List<CommandHandler> candidates = handlers.getOrDefault(token, List.of());
        for (CommandHandler h : candidates) {
            if (matches(h.scope(), ctx.isGroup())) {
                try {
                    h.handle(ctx);
                } catch (Exception e) {
                    logger.error("Handler {} failed", token, e);
                }
                return true;
            }
        }
        return false;
    }

    private boolean matches(CommandHandler.Scope scope, boolean group) {
        return scope == CommandHandler.Scope.ANY
                || (scope == CommandHandler.Scope.GROUP && group)
                || (scope == CommandHandler.Scope.DM && !group);
    }
}
