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
    public static SendMessageRequest plain(long chatId, String text) {
        return new SendMessageRequest(chatId, text, null, null, null);
    }
}
