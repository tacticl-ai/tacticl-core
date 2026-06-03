package io.tacticl.client.arbiter.conversation;

/**
 * Client for the arbiter's {@code ArbiterConversationService} — the conversational
 * persona brain. Phase 1 exposes a single streaming turn: text in, reply events out.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link ArbiterConversationGrpcClient} — the real gRPC client (registered when
 *       {@code tacticl.voice.arbiter-conversation.enabled=true}).</li>
 * </ul>
 * When the flag is off, no client bean exists and the voice plane uses its in-JVM
 * Anthropic fallback engine instead.
 */
public interface ConversationServiceClient {

    /**
     * Run one conversational persona turn. Non-blocking: returns immediately and
     * streams events to {@code listener} as they arrive.
     */
    void converseTurn(ConverseTurnInput input, ConverseEventListener listener);
}
