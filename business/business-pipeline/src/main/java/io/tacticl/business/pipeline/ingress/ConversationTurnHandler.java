package io.tacticl.business.pipeline.ingress;

/**
 * SPI the ingress front door uses to hand a {@link IngressKind#CONVERSATION_TURN} off to the
 * conversation subsystem, without {@code business-pipeline} depending on
 * {@code business-conversation} (which already depends on {@code business-pipeline} — a direct
 * dependency would be a cycle). The conversation module provides the implementation; the
 * dispatcher injects it optionally and treats its absence as "conversation handling not wired".
 *
 * <p>Mirrors the {@code PipelineEventChannel} SPI pattern: contract declared here, impl elsewhere.
 */
public interface ConversationTurnHandler {

    /**
     * Handle a free-text conversational turn that arrived over a transport.
     *
     * @param tacticlUserId resolved internal user id (already authorized/linked by the dispatcher)
     * @param origin        where the turn came from / where replies should be rendered
     * @param text          the user's message
     * @param canDispatch   whether this caller may, in this turn, authorize a side-effecting dispatch
     *                      (start a build). Owner channels (WEB/VOICE) act on the caller's own session
     *                      ⇒ true; shared channels (DISCORD/TELEGRAM) require EntryPoint admin. The
     *                      conversation brain's alignment gate fails CLOSED unless this is true.
     */
    void handleTurn(String tacticlUserId, RunOrigin origin, String text, boolean canDispatch);
}
