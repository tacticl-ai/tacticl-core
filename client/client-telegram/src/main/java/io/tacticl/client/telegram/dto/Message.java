package io.tacticl.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Message(
    long message_id,
    long date,
    Chat chat,
    User from,
    String text
) {}
