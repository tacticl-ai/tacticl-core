package io.tacticl.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Update(
    long update_id,
    Message message,
    Message edited_message,
    CallbackQuery callback_query,
    ChatMemberUpdate my_chat_member,
    ChatMemberUpdate chat_member
) {}
