package io.tacticl.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// is_forum uses Boolean (not primitive) because Jackson 3 rejects missing primitives
// on records with FAIL_ON_NULL_FOR_PRIMITIVES; Telegram omits the field for non-forums.
@JsonIgnoreProperties(ignoreUnknown = true)
public record Chat(long id, String type, String username, String first_name, String title,
                   Boolean is_forum) {}
