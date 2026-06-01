package io.tacticl.business.cloudorchestrator.routing;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import io.tacticl.data.cloudorchestrator.entity.SessionStatus;

/**
 * Pure routing function that picks the persona (or control action) for the next
 * conversation turn. No LLM call, no I/O — runs inside the workflow turn loop.
 *
 * <p>Implements SAD §7.1 verbatim. See also PRD §5.5. Lives as a Spring
 * {@code @Component} purely so it can be constructor-injected into
 * {@code CloudAgentSessionWorkflowImpl}'s invoking class; it has no
 * dependencies and holds no state.
 *
 * <p>Adding new personas or rules: edit {@link #route} directly. Telemetry
 * comes for free via the {@link RoutingDecision#reason()} on
 * {@link RoutingDecision.InvokePersona} / {@link RoutingDecision.ControlAction}.
 */
@Component
public class PersonaRouter {

    // --- Persona identifiers (match resources/conversational-personas/*.md) ---
    static final String PERSONA_PRODUCT_MANAGER = "product-manager";
    static final String PERSONA_MARKET_RESEARCHER = "market-researcher";

    // --- Regex patterns (compiled once; see SAD §7.1 for source-of-truth strings) ---
    static final Pattern MODE_CHANGE_TEXT_RE =
            Pattern.compile("(switch to|use|go to)\\s+(text|chat)|stop talking|stop voice",
                    Pattern.CASE_INSENSITIVE);

    static final Pattern MODE_CHANGE_VOICE_RE =
            Pattern.compile("(switch to|use|go to)\\s+voice|let'?s talk",
                    Pattern.CASE_INSENSITIVE);

    static final Pattern MUTE_RE =
            Pattern.compile("be quiet|mute|go quiet|shut up",
                    Pattern.CASE_INSENSITIVE);

    static final Pattern CANCEL_RE =
            Pattern.compile("cancel( this)?|abort( this)?|nevermind|forget it",
                    Pattern.CASE_INSENSITIVE);

    static final Pattern TOPIC_SHIFT_RE =
            Pattern.compile("^(build|implement|code|deploy|let'?s )",
                    Pattern.CASE_INSENSITIVE);

    static final Pattern MARKET_INTENT_RE =
            Pattern.compile(
                    "market|competitor|validate|demand|pricing|traction|TAM|SAM|customer interview|landscape",
                    Pattern.CASE_INSENSITIVE);

    /**
     * Select the next-turn persona (or a control action) for the given session.
     *
     * <p>Pure function: identical inputs MUST produce identical outputs.
     * No mutation of any argument, no I/O, no logging above TRACE.
     *
     * @param state          current session status
     * @param lastUserTurn   the just-received user turn (post-STT for voice)
     * @param pipelineState  currently-focused pipeline snapshot, if any
     *                       (reserved for future LLM-fallback in SAD §7.3; not
     *                       used in the v1 algorithm)
     * @param recentTurns    chronological tail of the session transcript
     *                       (oldest first; the LAST element is the most recent)
     * @return the routing decision; never null
     */
    public RoutingDecision route(
            SessionStatus state,
            Turn lastUserTurn,
            Optional<PipelineState> pipelineState,
            List<Turn> recentTurns) {

        final String userText = lastUserTurn.text() == null ? "" : lastUserTurn.text();

        // 1. Control intents (regex on transcript — no persona invoked)
        if (matches(MODE_CHANGE_TEXT_RE, userText)) {
            return new RoutingDecision.ControlAction(
                    RoutingDecision.ControlType.MODE_CHANGE_TEXT,
                    "user requested switch to text mode");
        }
        if (matches(MODE_CHANGE_VOICE_RE, userText)) {
            return new RoutingDecision.ControlAction(
                    RoutingDecision.ControlType.MODE_CHANGE_VOICE,
                    "user requested switch to voice mode");
        }
        if (matches(MUTE_RE, userText)) {
            return new RoutingDecision.ControlAction(
                    RoutingDecision.ControlType.MODE_CHANGE_MUTE,
                    "user requested mute");
        }
        if (matches(CANCEL_RE, userText)) {
            return new RoutingDecision.ControlAction(
                    RoutingDecision.ControlType.CANCEL_SESSION,
                    "user requested cancel");
        }

        // 2. Hard rules
        if (state == SessionStatus.PIPELINE_BLOCKED) {
            return new RoutingDecision.InvokePersona(
                    PERSONA_PRODUCT_MANAGER,
                    "pipeline checkpoint requires mediation");
        }

        // 3. Sticky persona — last assistant turn keeps the floor unless user shifts topic
        Turn lastAssistant = lastAssistantTurn(recentTurns);
        if (lastAssistant != null
                && PERSONA_MARKET_RESEARCHER.equals(lastAssistant.personaId())
                && !matches(TOPIC_SHIFT_RE, userText)) {
            return new RoutingDecision.InvokePersona(
                    PERSONA_MARKET_RESEARCHER,
                    "continuing research thread");
        }

        // 4. Market research intent detection
        if (matches(MARKET_INTENT_RE, userText)) {
            return new RoutingDecision.InvokePersona(
                    PERSONA_MARKET_RESEARCHER,
                    "market/competitor/validation intent detected");
        }

        // 5. Default
        return new RoutingDecision.InvokePersona(
                PERSONA_PRODUCT_MANAGER,
                "default chat persona");
    }

    private static boolean matches(Pattern p, String text) {
        return p.matcher(text).find();
    }

    /**
     * Most recent assistant turn in the supplied recent-turns tail, or null if none.
     * Walks backwards because the immediately-prior assistant turn is what carries
     * the "sticky persona" floor in SAD §7.1 step 3.
     */
    private static Turn lastAssistantTurn(List<Turn> recentTurns) {
        if (recentTurns == null || recentTurns.isEmpty()) {
            return null;
        }
        for (int i = recentTurns.size() - 1; i >= 0; i--) {
            Turn t = recentTurns.get(i);
            if (t != null && "assistant".equals(t.role())) {
                return t;
            }
        }
        return null;
    }
}
