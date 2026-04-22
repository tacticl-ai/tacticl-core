package io.tacticl.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PhotoSize(String file_id, String file_unique_id, int width, int height, long file_size) {}
