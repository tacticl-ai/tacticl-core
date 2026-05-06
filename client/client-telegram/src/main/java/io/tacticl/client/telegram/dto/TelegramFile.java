package io.tacticl.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Telegram {@code File} object returned by {@code getFile}.
 *
 * <p>Once obtained, the binary content can be downloaded from
 * {@code https://api.telegram.org/file/bot<token>/<file_path>}. The {@code file_path}
 * is valid for at least 1 hour from the {@code getFile} response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramFile(String file_id, String file_unique_id, Long file_size, String file_path) {}
