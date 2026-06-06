package io.tacticl.business.discord;

import io.tacticl.business.discord.identity.DiscordIdentityResolver;
import io.tacticl.business.pipeline.ingress.IngressDispatchService;
import io.tacticl.business.pipeline.ingress.IngressKind;
import io.tacticl.business.pipeline.ingress.IngressRequest;
import io.tacticl.client.discord.DiscordRestClient;
import io.tacticl.client.discord.config.DiscordConfig;
import io.tacticl.data.discord.entity.DiscordRunBinding;
import io.tacticl.data.discord.repository.DiscordRunBindingRepository;
import io.tacticl.data.pipeline.entity.PipelineRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Runs the Discord-specific orchestration AFTER the controller has already returned its deferred
 * acknowledgement to Discord. The controller calls {@link #dispatchAsync} on the shared pipeline
 * executor; this service then:
 *
 * <ol>
 *   <li>resolves the invoking Discord snowflake to a tacticl user id (hard link precondition —
 *       unlinked ⇒ a private "link your account" followup, never a dispatch),</li>
 *   <li>normalizes the interaction into a transport-neutral {@link IngressRequest} via
 *       {@link DiscordInboundAdapter} (pure),</li>
 *   <li>hands it to the channel-neutral {@link IngressDispatchService}, and</li>
 *   <li>for EXPLICIT_TRIGGER, persists a {@link DiscordRunBinding} (runId → channel) so
 *       {@link DiscordRunUpdateChannel} knows where to render later run updates.</li>
 * </ol>
 *
 * <p>The immediate user acknowledgement is sent as a follow-up to the deferred interaction (the
 * one place the interaction token is used). All durable run updates go to the channel via the bot
 * token in {@link DiscordRunUpdateChannel} — never the interaction token, which expires at ~15 min.
 */
@Service
@ConditionalOnProperty(name = "tacticl.discord.enabled", havingValue = "true")
public class DiscordInteractionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DiscordInteractionDispatcher.class);

    /** Discord interaction + application-command type constants for the {@code /link} fast-path. */
    private static final int TYPE_APPLICATION_COMMAND = 2;
    private static final int COMMAND_CHAT_INPUT = 1;
    private static final String LINK_COMMAND_NAME = "link";

    private final DiscordIdentityResolver identityResolver;
    private final DiscordInboundAdapter adapter;
    private final IngressDispatchService ingressDispatchService;
    private final DiscordRunBindingRepository bindingRepo;
    private final DiscordRestClient discord;
    private final DiscordUserLinker linker;
    private final DiscordConfig config;

    public DiscordInteractionDispatcher(DiscordIdentityResolver identityResolver,
                                        DiscordInboundAdapter adapter,
                                        IngressDispatchService ingressDispatchService,
                                        DiscordRunBindingRepository bindingRepo,
                                        DiscordRestClient discord,
                                        DiscordUserLinker linker,
                                        DiscordConfig config) {
        this.identityResolver = identityResolver;
        this.adapter = adapter;
        this.ingressDispatchService = ingressDispatchService;
        this.bindingRepo = bindingRepo;
        this.discord = discord;
        this.linker = linker;
        this.config = config;
    }

    /**
     * Asynchronously resolves identity, normalizes, dispatches, and acknowledges. Runs on the
     * shared {@code pipelineCallbackExecutor} so it does not block the webhook thread (Discord
     * requires the controller to ACK within 3 seconds — the deferred ACK is already sent before
     * this runs).
     *
     * @param interaction      the verified, deduped interaction payload
     * @param interactionToken the interaction token used only for the immediate followup
     */
    @Async("pipelineCallbackExecutor")
    public void dispatchAsync(Map<String, Object> interaction, String interactionToken) {
        try {
            dispatch(interaction, interactionToken);
        } catch (Exception e) {
            log.error("Discord async dispatch failed", e);
            safeFollowup(interactionToken, "⚠️ Could not process that interaction. Please try again.");
        }
    }

    private void dispatch(Map<String, Object> interaction, String interactionToken) {
        String discordUserId = extractInvokingUserId(interaction);

        // /link runs BEFORE the identity gate — by definition the user is not yet linked. The
        // interaction carries the snowflake; we mint a token the user redeems in the web app.
        if (isLinkCommand(interaction)) {
            handleLinkCommand(discordUserId, extractInvokingUsername(interaction), interactionToken);
            return;
        }

        Optional<String> tacticlUserId = identityResolver.resolve(discordUserId);

        if (tacticlUserId.isEmpty()) {
            // Hard precondition: never dispatch on behalf of an unlinked Discord identity.
            log.info("Discord interaction from unlinked user {} — prompting to link", discordUserId);
            safeFollowup(interactionToken,
                "🔗 Link your Tacticl account first: run `/link` here to get a code, then paste it in "
                    + "Tacticl (Settings → Integrations → Discord). Then retry.");
            return;
        }

        IngressRequest request = adapter.normalize(interaction, tacticlUserId.get());
        Optional<PipelineRun> run = ingressDispatchService.dispatch(request);

        if (request.kind() == IngressKind.EXPLICIT_TRIGGER && run.isPresent()) {
            persistBinding(run.get(), request);
            safeFollowup(interactionToken, "🚀 Pipeline started — updates will post in this channel.");
        } else if (request.kind() == IngressKind.CHECKPOINT_DECISION) {
            safeFollowup(interactionToken, "✅ Decision recorded.");
        } else if (request.kind() == IngressKind.CANCEL_RUN) {
            safeFollowup(interactionToken, "🛑 Pipeline cancelled.");
        }
    }

    /** Records runId → channel so {@link DiscordRunUpdateChannel} can render updates for this run. */
    private void persistBinding(PipelineRun run, IngressRequest request) {
        String channelId = request.origin().destinationHandle();
        if (channelId == null || channelId.isBlank()) {
            log.warn("Discord run {} has no destination channel — run updates will not render", run.getId());
            return;
        }
        bindingRepo.save(DiscordRunBinding.create(
            run.getId(), channelId, request.origin().threadHandle()));
    }

    /** True when the interaction is the {@code /link} CHAT_INPUT slash command. */
    @SuppressWarnings("unchecked")
    private boolean isLinkCommand(Map<String, Object> interaction) {
        if (asInt(interaction.get("type")) != TYPE_APPLICATION_COMMAND) {
            return false;
        }
        Object dataObj = interaction.get("data");
        if (dataObj instanceof Map<?, ?> data) {
            int commandType = data.get("type") instanceof Number n ? n.intValue() : COMMAND_CHAT_INPUT;
            return LINK_COMMAND_NAME.equals(data.get("name")) && commandType == COMMAND_CHAT_INPUT;
        }
        return false;
    }

    /** Mints a link token for the invoking snowflake and replies with it (ephemeral). */
    private void handleLinkCommand(String discordUserId, String discordUsername, String interactionToken) {
        if (discordUserId == null || discordUserId.isBlank()) {
            safeFollowup(interactionToken, "⚠️ Couldn't read your Discord id — try again.");
            return;
        }
        try {
            String token = linker.beginLink(discordUserId, discordUsername);
            safeFollowup(interactionToken,
                "🔗 To finish linking, paste this code in Tacticl (Settings → Integrations → Discord):\n"
                    + "`" + token + "`\nIt expires in " + config.getLinkTokenTtlMinutes() + " minutes.");
        } catch (Exception e) {
            log.warn("Discord /link failed for snowflake {}", discordUserId, e);
            safeFollowup(interactionToken, "⚠️ Couldn't generate a link code — try again.");
        }
    }

    @SuppressWarnings("unchecked")
    private String extractInvokingUsername(Map<String, Object> interaction) {
        Object memberObj = interaction.get("member");
        if (memberObj instanceof Map<?, ?> member && member.get("user") instanceof Map<?, ?> user) {
            Object username = user.get("username");
            if (username != null) {
                return String.valueOf(username);
            }
        }
        Object userObj = interaction.get("user");
        if (userObj instanceof Map<?, ?> user && user.get("username") != null) {
            return String.valueOf(user.get("username"));
        }
        return null;
    }

    private static int asInt(Object o) {
        return o instanceof Number n ? n.intValue() : -1;
    }

    @SuppressWarnings("unchecked")
    private String extractInvokingUserId(Map<String, Object> interaction) {
        // Guild interactions carry the invoker under "member.user.id"; DM interactions under "user.id".
        Object memberObj = interaction.get("member");
        if (memberObj instanceof Map<?, ?> member) {
            Object userObj = member.get("user");
            if (userObj instanceof Map<?, ?> user && user.get("id") != null) {
                return String.valueOf(user.get("id"));
            }
        }
        Object userObj = interaction.get("user");
        if (userObj instanceof Map<?, ?> user && user.get("id") != null) {
            return String.valueOf(user.get("id"));
        }
        return null;
    }

    /** Posts an ephemeral-style follow-up to the deferred interaction; never throws. */
    private void safeFollowup(String interactionToken, String content) {
        try {
            // flags=64 → ephemeral (only the invoking user sees the acknowledgement).
            discord.createFollowupMessage(interactionToken, Map.of("content", content, "flags", 64));
        } catch (Exception e) {
            log.warn("Discord followup failed (token window may have closed)", e);
        }
    }
}
