package io.tacticl.business.voice;

/**
 * The swap point for the conversational brain. A turn's transcript goes in; the
 * persona reply streams out through the {@link ConversationSink}.
 *
 * <p>Two implementations select by {@code tacticl.voice.arbiter-conversation.enabled}:
 * <ul>
 *   <li>{@link ArbiterConversationEngine} (primary) — the persona‑with‑skills runs
 *       in the arbiter (its final home); reached over gRPC.</li>
 *   <li>{@link AnthropicDirectConversationEngine} (fallback) — an in‑JVM Anthropic
 *       call, so local/dev and arbiter‑down still speak.</li>
 * </ul>
 *
 * <p>Implementations MUST NOT block the calling (STT) thread on the whole reply —
 * they emit deltas via the sink as they arrive. Exactly one terminal sink callback
 * ({@code onDone}/{@code onError}) fires per turn.
 */
public interface ConversationEngine {

    void converse(ConversationContext ctx, ConversationSink sink);
}
