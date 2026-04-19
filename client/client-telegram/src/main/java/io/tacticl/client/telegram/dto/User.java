package io.tacticl.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record User(long id, boolean is_bot, String username, String first_name) {}
