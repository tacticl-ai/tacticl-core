package io.tacticl.client.discord.gateway;

import io.tacticl.client.discord.dto.DiscordGatewayMessage;

/**
 * Callback the {@link DiscordGatewayClient} invokes for each inbound {@code MESSAGE_CREATE}.
 * The business layer ({@code business-discord}) implements this to bridge a free-form Discord
 * message into the conversation brain.
 *
 * <p>Contract: the gateway invokes this on its WebSocket read thread and isolates failures (a
 * thrown exception is logged and swallowed, never killing the connection). Implementations MUST
 * NOT block — hand any LLM / network work to their own executor and return quickly, or the
 * gateway's read pump (and heartbeat) stalls.
 */
@FunctionalInterface
public interface DiscordGatewayListener {

    void onMessageCreate(DiscordGatewayMessage message);
}
