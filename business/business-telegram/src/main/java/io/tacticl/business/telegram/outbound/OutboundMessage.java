package io.tacticl.business.telegram.outbound;

import io.tacticl.client.telegram.dto.SendMessageRequest;

public record OutboundMessage(SendMessageRequest request) {}
