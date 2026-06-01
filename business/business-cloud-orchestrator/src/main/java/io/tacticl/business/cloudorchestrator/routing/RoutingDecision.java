package io.tacticl.business.cloudorchestrator.routing;

/**
 * The result of {@link PersonaRouter#route} for a single user turn.
 *
 * <p>Either an {@link InvokePersona} (the workflow should execute the persona's
 * Anthropic call with the resolved system prompt + tools) or a {@link ControlAction}
 * (the workflow should apply the control side-effect — mode change, mute, cancel —
 * without invoking any persona LLM call).
 *
 * <p>See SAD §7.1 ("Persona Routing — Algorithm") for the routing function
 * specification this models.
 */
public sealed interface RoutingDecision permits RoutingDecision.InvokePersona, RoutingDecision.ControlAction {

    /** The router selected a persona; the workflow should invoke its LLM call. */
    record InvokePersona(String personaId, String reason) implements RoutingDecision {}

    /** The router detected a control intent; no persona is invoked. */
    record ControlAction(ControlType type, String reason) implements RoutingDecision {}

    /** Categories of conversation-control side-effects the router may emit (no persona invoked). */
    enum ControlType {
        MODE_CHANGE_TEXT,
        MODE_CHANGE_VOICE,
        MODE_CHANGE_MUTE,
        CANCEL_TTS,
        CANCEL_SESSION
    }
}
