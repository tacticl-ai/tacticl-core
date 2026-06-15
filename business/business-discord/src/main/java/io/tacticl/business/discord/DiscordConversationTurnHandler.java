package io.tacticl.business.discord;

import io.cidadel.framework.exception.CidadelException;
import io.tacticl.business.pipeline.ingress.ChannelType;
import io.tacticl.business.pipeline.ingress.ConversationTurnHandler;
import io.tacticl.business.pipeline.ingress.EntryPointResolver;
import io.tacticl.business.pipeline.ingress.IngressDispatchService;
import io.tacticl.business.pipeline.ingress.IngressKind;
import io.tacticl.business.pipeline.ingress.IngressRequest;
import io.tacticl.business.pipeline.ingress.RunOrigin;
import io.tacticl.client.arbiter.conversation.ConverseEventListener;
import io.tacticl.client.arbiter.conversation.ConverseTurnInput;
import io.tacticl.client.arbiter.conversation.ConversationServiceClient;
import io.tacticl.client.discord.DiscordRestClient;
import io.tacticl.data.discord.entity.DiscordRunBinding;
import io.tacticl.data.discord.repository.DiscordRunBindingRepository;
import io.tacticl.data.pipeline.entity.EntryPoint;
import io.tacticl.data.pipeline.entity.PipelineRun;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Renders the conversation brain into a Discord channel: it is the {@link ConversationTurnHandler}
 * for {@link ChannelType#DISCORD}. A free-form message (delivered by {@code DiscordGatewayBridge}
 * through the ingress front door as a {@code CONVERSATION_TURN}) is relayed to the arbiter brain;
 * the streamed reply is accumulated and posted back to the same channel, and a
 * {@code start_pipeline} tool call is dispatched through the SAME ingress path a slash trigger uses
 * (so {@code DiscordRunUpdateChannel} narrates the build back into the channel).
 *
 * <p>Conversation key = the channel snowflake, so every member of a channel contributes to ONE
 * shared brain session (a shared project effort); dispatch authority is still per-turn
 * ({@code canDispatch} = admin of the channel's EntryPoint), enforced server-side by the brain.
 *
 * <p>Gated on {@code tacticl.discord.gateway-enabled} — it only matters when the gateway delivers
 * free-form turns. If the arbiter conversation client is absent (its flag off) the turn is dropped
 * with a warning rather than failing.
 */
@Service
@ConditionalOnProperty(name = "tacticl.discord.gateway-enabled", havingValue = "true")
public class DiscordConversationTurnHandler implements ConversationTurnHandler {

    private static final Logger log = LoggerFactory.getLogger(DiscordConversationTurnHandler.class);
    private static final JsonMapper JSON = new JsonMapper();
    private static final String SKILL_START_PIPELINE = "start_pipeline";
    /** Discord hard-caps a message at 2000 chars; chunk longer replies rather than lose content. */
    private static final int DISCORD_MAX_MESSAGE = 2000;
    private static final String PRODUCT_TACTICL = "tacticl";

    private final ObjectProvider<ConversationServiceClient> conversationClient;
    private final DiscordRestClient discord;
    private final IngressDispatchService ingressDispatchService;
    private final DiscordRunBindingRepository runBindingRepository;
    private final EntryPointResolver entryPointResolver;
    /** Runs the (slow, network-bound) pipeline dispatch off the gRPC stream's callback thread. */
    private final ExecutorService dispatchExecutor =
        Executors.newFixedThreadPool(2, daemonFactory("discord-conv-dispatch"));

    public DiscordConversationTurnHandler(ObjectProvider<ConversationServiceClient> conversationClient,
                                          DiscordRestClient discord,
                                          IngressDispatchService ingressDispatchService,
                                          DiscordRunBindingRepository runBindingRepository,
                                          EntryPointResolver entryPointResolver) {
        this.conversationClient = conversationClient;
        this.discord = discord;
        this.ingressDispatchService = ingressDispatchService;
        this.runBindingRepository = runBindingRepository;
        this.entryPointResolver = entryPointResolver;
    }

    @PreDestroy
    void shutdown() {
        dispatchExecutor.shutdownNow();
    }

    @Override
    public boolean supports(ChannelType channel) {
        return channel == ChannelType.DISCORD;
    }

    @Override
    public void handleTurn(String tacticlUserId, RunOrigin origin, String text, boolean canDispatch) {
        if (origin == null || text == null || text.isBlank()) {
            return;
        }
        ConversationServiceClient client = conversationClient.getIfAvailable();
        if (client == null) {
            log.warn("Discord conversation turn but no arbiter conversation client is wired "
                     + "(tacticl.voice.arbiter-conversation.enabled?) — dropping (channel={})",
                     origin.destinationHandle());
            return;
        }
        // The channel's product scope (e.g. "strategiz" for a tenant channel). Resolve is also the
        // allowlist gate — a turn from a non-registered channel has no product and is dropped.
        String productId;
        try {
            productId = entryPointResolver.resolve(origin).getProductId();
        } catch (CidadelException e) {
            log.debug("Discord conversation turn from non-registered channel {} — dropping",
                      origin.destinationHandle());
            return;
        }
        if (productId == null || productId.isBlank()) {
            productId = PRODUCT_TACTICL;
        }

        ConverseTurnInput input = new ConverseTurnInput(
            productId,
            tacticlUserId,
            origin.destinationHandle(), // sessionId = channel snowflake → one shared brain session
            "t-" + UUID.randomUUID(),
            text,
            /* personaHint */ null,
            /* history — the persistent brain keeps its own session memory */ List.of(),
            /* locale */ null,
            /* githubToken — create_repo is off on Discord; dispatch uses the EntryPoint repo+token */ "",
            /* repos */ List.of(),
            /* pipelines — the brain can call run_status */ List.of(),
            canDispatch);

        client.converseTurn(input, new ChannelSink(tacticlUserId, origin));
    }

    /**
     * Accumulates the streamed reply and posts it to the channel on completion; dispatches a
     * {@code start_pipeline} tool call through ingress and binds the run for narration. One instance
     * per turn; gRPC serializes a stream's callbacks so no extra synchronization is needed.
     */
    private final class ChannelSink implements ConverseEventListener {

        private final String userId;
        private final RunOrigin origin;
        private final StringBuilder reply = new StringBuilder();
        private volatile boolean pipelineStarted;

        ChannelSink(String userId, RunOrigin origin) {
            this.userId = userId;
            this.origin = origin;
        }

        @Override
        public void onToken(String textDelta, String personaId) {
            if (textDelta != null && !textDelta.isEmpty()) {
                reply.append(textDelta);
            }
        }

        @Override
        public void onToolUse(String name, String inputJson, boolean terminal) {
            if (!SKILL_START_PIPELINE.equals(name)) {
                // create_repo and other arbiter-internal tools run inside the arbiter; ignore here.
                return;
            }
            String sparkInput = stringField(inputJson, "sparkInput", "spark_input");
            if (sparkInput == null || sparkInput.isBlank()) {
                log.warn("Discord start_pipeline missing sparkInput (channel={})", origin.destinationHandle());
                return;
            }
            String repoUrl = stringField(inputJson, "repoUrl", "repo_url");
            // Offload: dispatch runs sparkService.create + classify (a Claude call) + submitPipeline —
            // far too slow to run on the gRPC stream's callback thread. Returns the thread immediately.
            dispatchExecutor.submit(() -> dispatchPipeline(sparkInput, repoUrl));
        }

        /** Route a start_pipeline through the SAME ingress path a /pdlc trigger uses, then bind the run. */
        private void dispatchPipeline(String sparkInput, String repoUrl) {
            try {
                IngressRequest trigger = new IngressRequest(
                    origin, userId, IngressKind.EXPLICIT_TRIGGER, sparkInput,
                    List.of(), /* productHint */ null, origin.threadHandle(), /* decision */ null, repoUrl);
                Optional<PipelineRun> run = ingressDispatchService.dispatch(trigger);
                if (run.isEmpty()) {
                    return;
                }
                pipelineStarted = true;
                String runId = run.get().getId();
                try {
                    // Bind the run so DiscordRunUpdateChannel narrates events into this channel.
                    runBindingRepository.save(DiscordRunBinding.create(
                        runId, origin.destinationHandle(), origin.threadHandle()));
                } catch (Exception e) {
                    // A failed binding only means auto-narration won't route — don't lose the ack.
                    log.warn("Discord run binding save failed (run={}): {}", runId, e.toString());
                }
                post("🚀 Starting the build — I'll post progress here. (`" + runId + "`)");
            } catch (CidadelException e) {
                // Authority / entry-point failures: tell the channel cleanly rather than going silent.
                post("⚠️ I couldn't start that build: " + safe(e.getMessage()));
            } catch (Exception e) {
                log.warn("Discord pipeline dispatch failed (channel={}): {}",
                         origin.destinationHandle(), e.toString());
                post("⚠️ I couldn't start that build right now.");
            }
        }

        @Override
        public void onDone() {
            String text = reply.toString().trim();
            if (!text.isEmpty()) {
                post(text);
            } else if (!pipelineStarted) {
                log.debug("Discord turn produced no reply (channel={})", origin.destinationHandle());
            }
        }

        @Override
        public void onError(String userSafeMessage) {
            post("⚠️ " + (userSafeMessage == null || userSafeMessage.isBlank()
                ? "Sorry — I hit a snag. Try me again." : userSafeMessage));
        }

        /** Post to the channel, chunked under Discord's 2000-char limit; never throws. */
        private void post(String content) {
            if (content == null || content.isBlank()) {
                return;
            }
            try {
                for (String chunk : chunk(content, DISCORD_MAX_MESSAGE)) {
                    discord.createChannelMessage(origin.destinationHandle(),
                        java.util.Map.of("content", chunk));
                }
            } catch (Exception e) {
                log.warn("Discord reply post failed (channel={}): {}", origin.destinationHandle(), e.toString());
            }
        }
    }

    /** Split text into <= max-char chunks (Discord rejects messages over 2000 chars). */
    static List<String> chunk(String text, int max) {
        if (text.length() <= max) {
            return List.of(text);
        }
        java.util.List<String> parts = new java.util.ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(i + max, text.length());
            parts.add(text.substring(i, end));
            i = end;
        }
        return parts;
    }

    /** Read the first present, non-blank string field (camel or snake) from a tool-input JSON. */
    private static String stringField(String inputJson, String camel, String snake) {
        if (inputJson == null || inputJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = JSON.readTree(inputJson);
            JsonNode v = root.path(camel);
            if (v.isMissingNode() || v.isNull()) {
                v = root.path(snake);
            }
            String s = v.asString("");
            return s.isBlank() ? null : s;
        } catch (Exception e) {
            return null;
        }
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "something went wrong" : s;
    }

    private static ThreadFactory daemonFactory(String prefix) {
        AtomicLong counter = new AtomicLong();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
