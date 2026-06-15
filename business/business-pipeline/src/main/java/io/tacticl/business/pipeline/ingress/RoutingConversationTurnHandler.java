package io.tacticl.business.pipeline.ingress;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The single {@link ConversationTurnHandler} the {@link IngressDispatchService} sees, routing each
 * turn by {@link RunOrigin#channel()} to the leaf handler that can render the reply on that
 * transport (the voice sphere, a Discord channel, …).
 *
 * <p>Without this, two enabled leaf handlers (voice + discord) would make the dispatcher's
 * {@code ObjectProvider<ConversationTurnHandler>} ambiguous. Marked {@link Primary} so it wins that
 * lookup; it injects every leaf and excludes itself.
 *
 * <p>Lives in {@code business-pipeline} (the SPI's home) and references only the interface, so it
 * adds no dependency on the concrete handler modules — Spring injects the leaves at runtime.
 */
@Component
@Primary
public class RoutingConversationTurnHandler implements ConversationTurnHandler {

    private static final Logger log = LoggerFactory.getLogger(RoutingConversationTurnHandler.class);

    private final List<ConversationTurnHandler> delegates;

    public RoutingConversationTurnHandler(List<ConversationTurnHandler> handlers) {
        // Exclude self (we are also a ConversationTurnHandler bean) to avoid infinite recursion.
        this.delegates = handlers.stream().filter(h -> h != this).toList();
        log.info("Conversation turn routing wired across {} handler(s): {}", delegates.size(),
                 delegates.stream().map(h -> h.getClass().getSimpleName()).toList());
    }

    @Override
    public void handleTurn(String tacticlUserId, RunOrigin origin, String text, boolean canDispatch) {
        if (origin == null) {
            return;
        }
        ChannelType channel = origin.channel();
        ConversationTurnHandler delegate = delegates.stream()
            .filter(h -> h.supports(channel))
            .findFirst()
            .orElse(null);
        if (delegate == null) {
            log.warn("No conversation handler serves channel {} — dropping turn (user={})",
                     channel, tacticlUserId);
            return;
        }
        delegate.handleTurn(tacticlUserId, origin, text, canDispatch);
    }

    /** The router serves a channel iff some leaf does. */
    @Override
    public boolean supports(ChannelType channel) {
        return delegates.stream().anyMatch(h -> h.supports(channel));
    }
}
