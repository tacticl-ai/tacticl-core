package io.tacticl.client.arbiter.conversation;

/**
 * Sink for the streamed events of one {@code ConverseTurn}. Callbacks fire on the
 * gRPC executor thread (NOT the caller's thread); implementations must be
 * thread-safe with respect to the resources they touch.
 *
 * <p>Exactly one terminal callback fires per turn: {@link #onDone()} or
 * {@link #onError(String)}.
 */
public interface ConverseEventListener {

    /** The turn has begun; {@code personaId} is the persona the arbiter selected. */
    default void onStarted(String personaId) {
    }

    /** A reply text delta (the next chunk of spoken/displayed reply). */
    void onToken(String textDelta, String personaId);

    /**
     * The persona invoked a skill the CALLER must execute (e.g. {@code start_pipeline}).
     *
     * @param terminal true when this is the last event of the turn (Phase 1 side-effecting skills)
     */
    void onToolUse(String name, String inputJson, boolean terminal);

    /** The turn completed normally. */
    void onDone();

    /** The turn failed; {@code userSafeMessage} is safe to surface to the user. */
    void onError(String userSafeMessage);
}
