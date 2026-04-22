package io.tacticl.business.telegram.router;

import io.tacticl.client.telegram.dto.Message;

/**
 * Execution context passed to a {@link CommandHandler}. Wraps the raw
 * Telegram {@link Message} with convenience accessors for the common
 * dispatch decisions (chat type, args parsing).
 */
public record CommandContext(
        long chatId,
        long telegramUserId,
        String text,
        String senderUsername,
        Message raw) {

    /** Raw Telegram chat type: "private", "group", "supergroup", "channel". */
    public String chatType() {
        return raw.chat().type();
    }

    /** True if the command was sent from a group or supergroup. */
    public boolean isGroup() {
        String t = chatType();
        return "group".equals(t) || "supergroup".equals(t);
    }

    /**
     * Returns everything after the first space in {@link #text()}, trimmed.
     * Empty string if the text has no arguments.
     */
    public String argsAfterCommand() {
        int idx = text.indexOf(' ');
        return idx < 0 ? "" : text.substring(idx + 1).trim();
    }
}
