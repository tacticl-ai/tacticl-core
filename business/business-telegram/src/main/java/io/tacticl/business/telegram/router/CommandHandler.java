package io.tacticl.business.telegram.router;

/**
 * Contract for a Telegram slash-command handler.
 *
 * <p>Implementations are discovered as Spring beans and routed by
 * {@link TelegramCommandRouter} based on {@link #commandName()} and
 * {@link #scope()}.
 */
public interface CommandHandler {

    /** Where a command is permitted to be invoked from. */
    enum Scope {
        /** Group / supergroup chats only. */
        GROUP,
        /** Direct-message (private) chats only. */
        DM,
        /** Any chat type. */
        ANY
    }

    /** The command token including leading slash, e.g. {@code "/init"}. */
    String commandName();

    /** The chat scope in which this handler is valid. */
    Scope scope();

    /**
     * Short human-readable description shown in Telegram's command picker
     * (BotFather convention: ≤30 chars, no leading slash, no trailing period).
     *
     * <p>Defaults to {@link #commandName()} so handlers compile without changes;
     * override per-handler with proper user-facing text.
     */
    default String description() {
        return commandName();
    }

    /** Execute the command. */
    void handle(CommandContext ctx);
}
