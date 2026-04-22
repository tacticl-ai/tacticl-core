package io.tacticl.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMemberUpdate(
    Chat chat,
    User from,
    long date,
    ChatMember old_chat_member,
    ChatMember new_chat_member
) {}
