package io.tacticl.business.voice;

/**
 * Receives a {@link ConversationEngine}'s streamed output for one turn. The voice
 * handler implements this to drive the sphere: stream reply text, execute any
 * caller-side skill the persona invoked, and speak/settle on completion.
 *
 * <p>Callbacks may arrive on a gRPC executor thread (arbiter engine) or the calling
 * thread (in-JVM fallback). Exactly one terminal callback fires per turn.
 */
public interface ConversationSink {

    /** A reply text delta. */
    void onToken(String delta);

    /**
     * The persona invoked a side-effecting skill the caller must execute
     * (Phase 1: {@code start_pipeline}).
     */
    void onToolUse(String name, String inputJson);

    /** The turn completed; speak/settle. */
    void onDone();

    /** The turn failed; {@code userSafeMessage} is safe to surface. */
    void onError(String userSafeMessage);
}
