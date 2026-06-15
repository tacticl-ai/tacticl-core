package io.tacticl.business.discord;

import io.cidadel.framework.exception.CidadelException;
import io.tacticl.business.discord.identity.DiscordIdentityResolver;
import io.tacticl.business.pipeline.ingress.ChannelType;
import io.tacticl.business.pipeline.ingress.EntryPointResolver;
import io.tacticl.business.pipeline.ingress.IngressDispatchService;
import io.tacticl.business.pipeline.ingress.IngressKind;
import io.tacticl.business.pipeline.ingress.IngressRequest;
import io.tacticl.business.pipeline.ingress.RunOrigin;
import io.tacticl.client.discord.DiscordRestClient;
import io.tacticl.client.discord.dto.DiscordGatewayMessage;
import io.tacticl.client.discord.gateway.DiscordGatewayClient;
import io.tacticl.client.discord.gateway.DiscordGatewayListener;
import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Drives the Discord Gateway: starts the WebSocket on app-ready, and bridges each inbound free-form
 * {@code MESSAGE_CREATE} into the conversation brain through the ingress front door as a
 * {@code CONVERSATION_TURN}. The reply is rendered back to the channel by
 * {@link DiscordConversationTurnHandler}.
 *
 * <p>Gates (in order, so the bot only ever reacts in channels it's meant to):
 * <ol>
 *   <li>drop bot-authored and empty messages (no loops, no noise);</li>
 *   <li>dedup by message id (the gateway can redeliver on RESUME);</li>
 *   <li>ALLOWLIST: the channel must resolve to a registered {@code EntryPoint} — otherwise the bot
 *       stays silent (no link prompts, no conversation in public channels);</li>
 *   <li>identity: an unlinked author is prompted once to link; a linked author's turn is dispatched.</li>
 * </ol>
 *
 * <p>All per-message work runs on a dedicated executor — never the WebSocket read thread (a Mongo
 * round-trip there would stall the read pump and heartbeat). Gated on
 * {@code tacticl.discord.gateway-enabled}.
 */
@Component
@ConditionalOnProperty(name = "tacticl.discord.gateway-enabled", havingValue = "true")
public class DiscordGatewayBridge implements DiscordGatewayListener {

    private static final Logger log = LoggerFactory.getLogger(DiscordGatewayBridge.class);
    private static final int DEDUP_CAPACITY = 2_000;
    private static final int LINK_PROMPT_CAPACITY = 500;

    private final DiscordGatewayClient gatewayClient;
    private final DiscordIdentityResolver identityResolver;
    private final EntryPointResolver entryPointResolver;
    private final IngressDispatchService ingressDispatchService;
    private final DiscordRestClient discord;

    private final ExecutorService workers =
        Executors.newFixedThreadPool(4, namedDaemonFactory("discord-gw-work"));

    /** Bounded LRU sets: dedup processed message ids, and remember who we've already nudged to link. */
    private final Map<String, Boolean> seenMessages = boundedSet(DEDUP_CAPACITY);
    private final Map<String, Boolean> linkPrompted = boundedSet(LINK_PROMPT_CAPACITY);

    public DiscordGatewayBridge(DiscordGatewayClient gatewayClient,
                                DiscordIdentityResolver identityResolver,
                                EntryPointResolver entryPointResolver,
                                IngressDispatchService ingressDispatchService,
                                DiscordRestClient discord) {
        this.gatewayClient = gatewayClient;
        this.identityResolver = identityResolver;
        this.entryPointResolver = entryPointResolver;
        this.ingressDispatchService = ingressDispatchService;
        this.discord = discord;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startGateway() {
        try {
            gatewayClient.start(this);
            log.info("Discord gateway bridge started");
        } catch (Exception e) {
            // WHY: a gateway/token problem at boot must not brick the application.
            log.warn("Discord gateway failed to start — continuing without free-form chat", e);
        }
    }

    @PreDestroy
    public void stop() {
        try {
            gatewayClient.shutdown();
        } catch (Exception ignore) {
            // best-effort shutdown
        }
        workers.shutdownNow();
    }

    @Override
    public void onMessageCreate(DiscordGatewayMessage message) {
        // Cheap filters on the read thread; everything that needs I/O is handed to a worker.
        if (message == null || message.channelId() == null) {
            return;
        }
        // Self-loop defense (belt and suspenders): drop bot-authored, webhook, and our-own messages —
        // never react to anything we (or another bot/webhook) posted, or we could reply to ourselves.
        if (message.authorBot() || message.webhookId() != null) {
            return;
        }
        String botId = gatewayClient.getBotUserId();
        if (botId != null && botId.equals(message.authorId())) {
            return;
        }
        if (message.content() == null || message.content().isBlank()) {
            return; // attachments-only / system messages — nothing to converse about
        }
        if (message.id() != null && seenMessages.put(message.id(), Boolean.TRUE) != null) {
            return; // already processed (gateway redelivery on RESUME)
        }
        workers.submit(() -> process(message));
    }

    private void process(DiscordGatewayMessage m) {
        try {
            RunOrigin origin = new RunOrigin(
                ChannelType.DISCORD,
                (m.guildId() != null ? m.guildId() : "") + ":" + m.channelId(),
                m.channelId(),
                m.id());

            // ALLOWLIST gate: only registered channels may talk to the brain.
            try {
                entryPointResolver.resolve(origin);
            } catch (CidadelException notRegistered) {
                log.debug("Discord message in non-registered channel {} — ignoring", m.channelId());
                return;
            }

            // Identity gate: unlinked authors get one nudge; linked authors converse.
            var tacticlUserId = identityResolver.resolve(m.authorId());
            if (tacticlUserId.isEmpty()) {
                promptLinkOnce(m);
                return;
            }

            IngressRequest request = new IngressRequest(
                origin, tacticlUserId.get(), IngressKind.CONVERSATION_TURN, m.content(),
                List.of(), /* productHint */ null, /* correlationId */ m.id(), /* decision */ null);
            ingressDispatchService.dispatch(request);
        } catch (Exception e) {
            log.warn("Discord gateway message handling failed (channel={} msg={}): {}",
                     m.channelId(), m.id(), e.toString());
        }
    }

    /** Nudge an unlinked author to link — once per author (bounded), to avoid channel spam. */
    private void promptLinkOnce(DiscordGatewayMessage m) {
        if (m.authorId() == null || linkPrompted.put(m.authorId(), Boolean.TRUE) != null) {
            return;
        }
        try {
            discord.createChannelMessage(m.channelId(), Map.of(
                "content", "🔗 <@" + m.authorId() + "> link your Tacticl account to chat with me: "
                    + "run `/link` here for a code, then redeem it in Tacticl (Settings → Integrations → Discord)."));
        } catch (Exception e) {
            log.warn("Discord link prompt failed (channel={}): {}", m.channelId(), e.toString());
        }
    }

    private static Map<String, Boolean> boundedSet(int capacity) {
        return Collections.synchronizedMap(new LinkedHashMap<>(capacity + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > capacity;
            }
        });
    }

    private static ThreadFactory namedDaemonFactory(String prefix) {
        AtomicLong counter = new AtomicLong();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
