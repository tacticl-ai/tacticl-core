package io.tacticl.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ForumTopic(
    long message_thread_id,
    String name,
    Integer icon_color,
    String icon_custom_emoji_id
) {}
