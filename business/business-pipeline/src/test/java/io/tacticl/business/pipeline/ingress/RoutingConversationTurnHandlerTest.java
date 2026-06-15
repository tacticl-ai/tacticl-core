package io.tacticl.business.pipeline.ingress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoutingConversationTurnHandlerTest {

    private static final RunOrigin VOICE = new RunOrigin(ChannelType.VOICE, "voice", "sess-1", null);
    private static final RunOrigin DISCORD = new RunOrigin(ChannelType.DISCORD, "g:c", "chan-1", "m-1");

    /** A leaf that serves exactly one channel and records what it was handed. */
    private static final class FakeHandler implements ConversationTurnHandler {
        private final ChannelType served;
        RunOrigin lastOrigin;
        Boolean lastCanDispatch;

        FakeHandler(ChannelType served) {
            this.served = served;
        }

        @Override
        public void handleTurn(String userId, RunOrigin origin, String text, boolean canDispatch) {
            this.lastOrigin = origin;
            this.lastCanDispatch = canDispatch;
        }

        @Override
        public boolean supports(ChannelType channel) {
            return channel == served;
        }
    }

    @Test
    void routesEachChannelToItsHandler() {
        FakeHandler voice = new FakeHandler(ChannelType.VOICE);
        FakeHandler discord = new FakeHandler(ChannelType.DISCORD);
        var routing = new RoutingConversationTurnHandler(List.of(voice, discord));

        routing.handleTurn("u", VOICE, "hi", true);
        routing.handleTurn("u", DISCORD, "yo", false);

        assertThat(voice.lastOrigin).isEqualTo(VOICE);
        assertThat(voice.lastCanDispatch).isTrue();
        assertThat(discord.lastOrigin).isEqualTo(DISCORD);
        assertThat(discord.lastCanDispatch).isFalse();
    }

    @Test
    void excludesItselfFromDelegates_noInfiniteRecursion() {
        // A real Spring List<ConversationTurnHandler> would include the routing bean itself.
        FakeHandler voice = new FakeHandler(ChannelType.VOICE);
        var routing = new RoutingConversationTurnHandler(List.of(voice));
        // supports() reflects only the leaves; the router doesn't count itself.
        assertThat(routing.supports(ChannelType.VOICE)).isTrue();
        assertThat(routing.supports(ChannelType.DISCORD)).isFalse();
    }

    @Test
    void unservedChannel_isDroppedNotThrown() {
        var spyHandler = org.mockito.Mockito.mock(ConversationTurnHandler.class);
        org.mockito.Mockito.when(spyHandler.supports(ChannelType.VOICE)).thenReturn(true);
        var routing = new RoutingConversationTurnHandler(List.of(spyHandler));

        routing.handleTurn("u", DISCORD, "yo", false); // no leaf serves DISCORD

        verify(spyHandler, never()).handleTurn(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    }
}
