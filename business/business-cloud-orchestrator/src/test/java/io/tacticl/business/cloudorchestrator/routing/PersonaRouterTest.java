package io.tacticl.business.cloudorchestrator.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.tacticl.data.cloudorchestrator.entity.SessionStatus;

/**
 * Unit tests for {@link PersonaRouter}. Pure-function tests — no mocks, no
 * Spring context. Each test asserts on a specific branch of the SAD §7.1 algorithm.
 */
class PersonaRouterTest {

    private final PersonaRouter router = new PersonaRouter();

    // --- Helpers -----------------------------------------------------------

    private static Turn userTurn(String text) {
        return new Turn("user", null, text);
    }

    private static Turn assistantTurn(String personaId, String text) {
        return new Turn("assistant", personaId, text);
    }

    private static RoutingDecision routeUser(PersonaRouter router, SessionStatus state, String userText) {
        return router.route(state, userTurn(userText), Optional.empty(), List.of());
    }

    // --- 1. Control intents -----------------------------------------------

    @Test
    @DisplayName("control: 'switch to text' returns MODE_CHANGE_TEXT")
    void route_returnsModeChangeText_whenUserSaysSwitchToText() {
        RoutingDecision d = routeUser(router, SessionStatus.ENGAGED, "Switch to text please");

        assertThat(d).isInstanceOfSatisfying(RoutingDecision.ControlAction.class, ca -> {
            assertThat(ca.type()).isEqualTo(RoutingDecision.ControlType.MODE_CHANGE_TEXT);
            assertThat(ca.reason()).isNotBlank();
        });
    }

    @Test
    @DisplayName("control: 'stop talking' returns MODE_CHANGE_TEXT")
    void route_returnsModeChangeText_whenUserSaysStopTalking() {
        RoutingDecision d = routeUser(router, SessionStatus.ENGAGED, "stop talking");

        assertThat(d).isInstanceOfSatisfying(RoutingDecision.ControlAction.class,
                ca -> assertThat(ca.type()).isEqualTo(RoutingDecision.ControlType.MODE_CHANGE_TEXT));
    }

    @Test
    @DisplayName("control: 'let's talk' returns MODE_CHANGE_VOICE")
    void route_returnsModeChangeVoice_whenUserSaysSwitchToVoice() {
        RoutingDecision d = routeUser(router, SessionStatus.ENGAGED, "Let's talk");

        assertThat(d).isInstanceOfSatisfying(RoutingDecision.ControlAction.class,
                ca -> assertThat(ca.type()).isEqualTo(RoutingDecision.ControlType.MODE_CHANGE_VOICE));
    }

    @Test
    @DisplayName("control: 'be quiet' returns MODE_CHANGE_MUTE")
    void route_returnsMute_whenUserSaysBeQuiet() {
        RoutingDecision d = routeUser(router, SessionStatus.ENGAGED, "Be quiet for a sec");

        assertThat(d).isInstanceOfSatisfying(RoutingDecision.ControlAction.class,
                ca -> assertThat(ca.type()).isEqualTo(RoutingDecision.ControlType.MODE_CHANGE_MUTE));
    }

    @Test
    @DisplayName("control: 'abort this' returns CANCEL_SESSION")
    void route_returnsCancel_whenUserSaysAbort() {
        RoutingDecision d = routeUser(router, SessionStatus.ENGAGED, "Abort this");

        assertThat(d).isInstanceOfSatisfying(RoutingDecision.ControlAction.class,
                ca -> assertThat(ca.type()).isEqualTo(RoutingDecision.ControlType.CANCEL_SESSION));
    }

    @Test
    @DisplayName("control: 'nevermind' returns CANCEL_SESSION")
    void route_returnsCancel_whenUserSaysNevermind() {
        RoutingDecision d = routeUser(router, SessionStatus.ENGAGED, "nevermind");

        assertThat(d).isInstanceOfSatisfying(RoutingDecision.ControlAction.class,
                ca -> assertThat(ca.type()).isEqualTo(RoutingDecision.ControlType.CANCEL_SESSION));
    }

    // --- 2. Hard rules ----------------------------------------------------

    @Test
    @DisplayName("hard rule: PIPELINE_BLOCKED routes to product-manager regardless of intent")
    void route_returnsProductManager_whenStateIsPipelineBlocked() {
        // Use text that would otherwise route to market-researcher to prove the hard rule wins.
        RoutingDecision d = routeUser(router, SessionStatus.PIPELINE_BLOCKED,
                "what's the competitor landscape look like");

        assertThat(d).isInstanceOfSatisfying(RoutingDecision.InvokePersona.class, ip -> {
            assertThat(ip.personaId()).isEqualTo("product-manager");
            assertThat(ip.reason()).contains("checkpoint");
        });
    }

    @Test
    @DisplayName("hard rule beats control: ... but control intents come FIRST")
    void route_returnsControlAction_evenWhenPipelineBlocked() {
        // Control intents are step 1 — they outrank the hard rule.
        RoutingDecision d = routeUser(router, SessionStatus.PIPELINE_BLOCKED, "cancel this");

        assertThat(d).isInstanceOfSatisfying(RoutingDecision.ControlAction.class,
                ca -> assertThat(ca.type()).isEqualTo(RoutingDecision.ControlType.CANCEL_SESSION));
    }

    // --- 3. Sticky persona ------------------------------------------------

    @Test
    @DisplayName("sticky: market-researcher keeps the floor when user does not shift topic")
    void route_returnsMarketResearcher_stickyFromPriorTurn_whenUserDoesntShiftTopic() {
        List<Turn> recent = List.of(
                userTurn("who are the competitors in this space"),
                assistantTurn("market-researcher", "I'm seeing three direct competitors..."));

        RoutingDecision d = router.route(
                SessionStatus.ENGAGED,
                userTurn("any of them open source?"),
                Optional.empty(),
                recent);

        assertThat(d).isInstanceOfSatisfying(RoutingDecision.InvokePersona.class, ip -> {
            assertThat(ip.personaId()).isEqualTo("market-researcher");
            assertThat(ip.reason()).contains("research");
        });
    }

    @Test
    @DisplayName("sticky beaten by topic shift: 'let's build' flips back to PM")
    void route_returnsProductManager_whenStickyMarketResearcher_butUserSaysLetsBuild() {
        List<Turn> recent = List.of(
                userTurn("competitor research please"),
                assistantTurn("market-researcher", "I'm seeing three direct competitors..."));

        RoutingDecision d = router.route(
                SessionStatus.ENGAGED,
                userTurn("let's build something better than them"),
                Optional.empty(),
                recent);

        assertThat(d).isInstanceOfSatisfying(RoutingDecision.InvokePersona.class,
                ip -> assertThat(ip.personaId()).isEqualTo("product-manager"));
    }

    @Test
    @DisplayName("sticky beaten by topic shift: 'implement X' flips back to PM")
    void route_returnsProductManager_whenStickyMarketResearcher_butUserSaysImplement() {
        List<Turn> recent = List.of(
                assistantTurn("market-researcher", "Pricing in this space ranges $10–$50/mo."));

        RoutingDecision d = router.route(
                SessionStatus.ENGAGED,
                userTurn("Implement the checkout flow"),
                Optional.empty(),
                recent);

        assertThat(d).isInstanceOfSatisfying(RoutingDecision.InvokePersona.class,
                ip -> assertThat(ip.personaId()).isEqualTo("product-manager"));
    }

    @Test
    @DisplayName("sticky does NOT apply when the prior assistant was someone else (e.g. PM)")
    void route_doesNotApplyStickyForProductManager() {
        // The router only sticks for market-researcher in v1.
        List<Turn> recent = List.of(
                assistantTurn("product-manager", "Got it. What's the success criterion?"));

        RoutingDecision d = router.route(
                SessionStatus.ENGAGED,
                userTurn("hmm, not sure yet"),  // generic, no market intent
                Optional.empty(),
                recent);

        assertThat(d).isInstanceOfSatisfying(RoutingDecision.InvokePersona.class, ip -> {
            assertThat(ip.personaId()).isEqualTo("product-manager");
            assertThat(ip.reason()).contains("default");
        });
    }

    // --- 4. Market research intent ----------------------------------------

    @Test
    @DisplayName("intent: 'competitor' routes to market-researcher")
    void route_returnsMarketResearcher_whenUserMentionsCompetitor() {
        RoutingDecision d = routeUser(router, SessionStatus.ENGAGED,
                "any competitor doing this already?");

        assertThat(d).isInstanceOfSatisfying(RoutingDecision.InvokePersona.class, ip -> {
            assertThat(ip.personaId()).isEqualTo("market-researcher");
            assertThat(ip.reason()).contains("market");
        });
    }

    @Test
    @DisplayName("intent: 'TAM' routes to market-researcher")
    void route_returnsMarketResearcher_whenUserMentionsTAM() {
        RoutingDecision d = routeUser(router, SessionStatus.ENGAGED, "What's the TAM here?");

        assertThat(d).isInstanceOfSatisfying(RoutingDecision.InvokePersona.class,
                ip -> assertThat(ip.personaId()).isEqualTo("market-researcher"));
    }

    @Test
    @DisplayName("intent: 'customer interview' routes to market-researcher")
    void route_returnsMarketResearcher_whenUserMentionsCustomerInterview() {
        RoutingDecision d = routeUser(router, SessionStatus.ENGAGED,
                "Help me prep a customer interview");

        assertThat(d).isInstanceOfSatisfying(RoutingDecision.InvokePersona.class,
                ip -> assertThat(ip.personaId()).isEqualTo("market-researcher"));
    }

    // --- 5. Default --------------------------------------------------------

    @Test
    @DisplayName("default: product-manager when no other rule matches")
    void route_returnsProductManager_default_whenNoOtherRulesMatch() {
        RoutingDecision d = routeUser(router, SessionStatus.ENGAGED,
                "I want to add user authentication");

        assertThat(d).isInstanceOfSatisfying(RoutingDecision.InvokePersona.class, ip -> {
            assertThat(ip.personaId()).isEqualTo("product-manager");
            assertThat(ip.reason()).contains("default");
        });
    }

    @Test
    @DisplayName("default: empty/null text falls through to product-manager")
    void route_returnsProductManager_whenTextIsEmpty() {
        RoutingDecision d = routeUser(router, SessionStatus.ENGAGED, "");

        assertThat(d).isInstanceOfSatisfying(RoutingDecision.InvokePersona.class,
                ip -> assertThat(ip.personaId()).isEqualTo("product-manager"));
    }

    @Test
    @DisplayName("default: null text is treated as empty, not NPE")
    void route_returnsProductManager_whenTextIsNull() {
        RoutingDecision d = router.route(
                SessionStatus.ENGAGED,
                new Turn("user", null, null),
                Optional.empty(),
                List.of());

        assertThat(d).isInstanceOfSatisfying(RoutingDecision.InvokePersona.class,
                ip -> assertThat(ip.personaId()).isEqualTo("product-manager"));
    }

    // --- Purity ------------------------------------------------------------

    @Test
    @DisplayName("pure function: identical inputs return identical outputs across calls")
    void route_isPureFunction_noSideEffects() {
        SessionStatus state = SessionStatus.ENGAGED;
        Turn lastUser = userTurn("what does the market for this look like");
        Optional<PipelineState> pipe = Optional.empty();
        List<Turn> recent = List.of(assistantTurn("product-manager", "Tell me more."));

        RoutingDecision first = router.route(state, lastUser, pipe, recent);
        RoutingDecision second = router.route(state, lastUser, pipe, recent);
        RoutingDecision third = router.route(state, lastUser, pipe, recent);

        // Records have value-based equals — identical decisions across N invocations
        // prove no hidden state mutation.
        assertThat(first).isEqualTo(second).isEqualTo(third);
        assertThat(first).isInstanceOfSatisfying(RoutingDecision.InvokePersona.class,
                ip -> assertThat(ip.personaId()).isEqualTo("market-researcher"));
    }
}
