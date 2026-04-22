package io.tacticl.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Message(
    long message_id,
    long date,
    Chat chat,
    User from,
    String text,
    List<MessageEntity> entities,
    Voice voice,
    List<PhotoSize> photo,
    Document document,
    Long migrate_to_chat_id,
    Long migrate_from_chat_id,
    Long message_thread_id,
    Boolean is_topic_message,
    Message reply_to_message
) {}
