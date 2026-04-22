package io.tacticl.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Document(String file_id, String file_unique_id, String file_name, String mime_type, long file_size) {}
