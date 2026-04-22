package io.tacticl.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendMessageRequest(
    long chat_id,
    String text,
    String parse_mode,
    InlineKeyboardMarkup reply_markup,
    Integer message_thread_id
) {
    // Keep a 4-arg convenience constructor so existing call sites compile unchanged.
    public SendMessageRequest(long chat_id, String text, String parse_mode, InlineKeyboardMarkup reply_markup) {
        this(chat_id, text, parse_mode, reply_markup, null);
    }

    public static SendMessageRequest plain(long chatId, String text) {
        return new SendMessageRequest(chatId, text, null, null, null);
    }
}
