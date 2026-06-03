package io.tacticl.client.arbiter.conversation;

/**
 * One prior conversational turn supplied as context to {@code ConverseTurn}.
 * Provider-neutral; the gRPC client maps it onto the proto {@code Turn}.
 *
 * @param role      "user" | "assistant"
 * @param text      the turn text
 * @param personaId optional producing persona id (for sticky-persona routing); may be null
 */
public record ConvTurn(String role, String text, String personaId) {
}
