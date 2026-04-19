package io.tacticl.client.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookInfo(String url, boolean has_custom_certificate, int pending_update_count) {}
