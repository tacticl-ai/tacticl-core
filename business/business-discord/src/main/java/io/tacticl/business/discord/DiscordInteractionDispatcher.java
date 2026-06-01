package io.tacticl.business.discord;

import io.tacticl.business.discord.identity.DiscordIdentityResolver;
import io.tacticl.business.pipeline.ingress.IngressDispatchService;
import io.tacticl.business.pipeline.ingress.IngressKind;
import io.tacticl.business.pipeline.ingress.IngressRequest;
import io.tacticl.client.discord.DiscordRestClient;
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

    private final DiscordIdentityResolver identityResolver;
    private final DiscordInboundAdapter adapter;
    private final IngressDispatchService ingressDispatchService;
    private final DiscordRunBindingRepository bindingRepo;
    private final DiscordRestClient discord;

    public DiscordInteractionDispatcher(DiscordIdentityResolver identityResolver,
                                        DiscordInboundAdapter adapter,
                                        IngressDispatchService ingressDispatchService,
                                        DiscordRunBindingRepository bindingRepo,
                                        DiscordRestClient discord) {
        this.identityResolver = identityResolver;
        this.adapter = adapter;
        this.ingressDispatchService = ingressDispatchService;
        this.bindingRepo = bindingRepo;
        this.discord = discord;
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
        Optional<String> tacticlUserId = identityResolver.resolve(discordUserId);

        if (tacticlUserId.isEmpty()) {
            // Hard precondition: never dispatch on behalf of an unlinked Discord identity.
            log.info("Discord interaction from unlinked user {} — prompting to link", discordUserId);
            safeFollowup(interactionToken,
                "🔗 Link your Tacticl account first (Settings → Integrations → Discord), then retry.");
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
