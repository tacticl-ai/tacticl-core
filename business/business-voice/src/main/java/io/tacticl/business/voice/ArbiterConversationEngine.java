package io.tacticl.business.voice;

import io.strategiz.social.client.github.config.GitHubConfig;
import io.tacticl.business.profile.service.UserRepoService;
import io.tacticl.client.arbiter.conversation.ConvPipelineRef;
import io.tacticl.client.arbiter.conversation.ConvRepoRef;
import io.tacticl.client.arbiter.conversation.ConvTurn;
import io.tacticl.client.arbiter.conversation.ConverseEventListener;
import io.tacticl.client.arbiter.conversation.ConverseTurnInput;
import io.tacticl.client.arbiter.conversation.ConversationServiceClient;
import io.tacticl.data.pipeline.entity.PhaseState;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.entity.PipelineStatus;
import io.tacticl.data.pipeline.entity.RoleState;
import io.tacticl.data.pipeline.repository.PipelineRunRepository;
import io.tacticl.data.profile.entity.UserRepo;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Primary {@link ConversationEngine}: the conversational persona runs in the
 * arbiter (its final home — persona prompt, skills, multi‑provider routing); this
 * is a thin gRPC delegate. Active when
 * {@code tacticl.voice.arbiter-conversation.enabled=true} (prod).
 *
 * <p>It owns no persona prompt and no LLM logic — it maps the turn onto a
 * {@code ConverseTurn} request and relays the streamed events onto the
 * {@link ConversationSink}. When the arbiter eventually also owns STT/TTS
 * (Phase 3), this engine and the whole tacticl‑core voice plane retire.
 */
@Service
@ConditionalOnProperty(name = "tacticl.voice.arbiter-conversation.enabled", havingValue = "true")
public class ArbiterConversationEngine implements ConversationEngine {

    private final ConversationServiceClient client;

    private final VoiceProperties properties;

    private final GitHubConfig gitHubConfig;

    private final UserRepoService userRepoService;

    private final PipelineRunRepository pipelineRunRepository;

    /** Max in-flight pipelines surfaced as grounding (recent-first), to cap prompt size. */
    private static final int MAX_PIPELINE_GROUNDING = 5;

    public ArbiterConversationEngine(ConversationServiceClient client,
                                     VoiceProperties properties,
                                     GitHubConfig gitHubConfig,
                                     UserRepoService userRepoService,
                                     PipelineRunRepository pipelineRunRepository) {
        this.client = client;
        this.properties = properties;
        this.gitHubConfig = gitHubConfig;
        this.userRepoService = userRepoService;
        this.pipelineRunRepository = pipelineRunRepository;
    }

    @Override
    public void converse(ConversationContext ctx, ConversationSink sink) {
        ConverseTurnInput input = new ConverseTurnInput(
            properties.getProductId(),
            ctx.userId(),
            ctx.sessionId(),
            ctx.turnId(),
            ctx.userText(),
            /* personaHint — let the arbiter route */ null,
            mapHistory(ctx.history()),
            /* locale */ null,
            // The Tacticl PAT (Vault github.app-token) so the arbiter's create_repo skill
            // can provision a repo for this build. Null/blank ⇒ provisioning simply off.
            gitHubConfig.getAppToken(),
            // The user's known repos as grounding — the analyst offers them once requirements
            // are understood. Recent-first, capped; empty for a first-time user.
            mapRepos(ctx.userId()),
            // The user's in-flight pipelines as grounding, so the persona knows what's building
            // and can answer "how's it going / has the PM finished?" without a tool call. The
            // arbiter renders these into the system prompt every turn.
            mapPipelines(ctx.userId()),
            // Whether THIS turn's caller may authorize a dispatch. The arbiter's alignment gate fails
            // CLOSED unless true — a non-admin "yes" never fires create_repo / start_pipeline.
            ctx.canDispatch());

        client.converseTurn(input, new ConverseEventListener() {
            @Override
            public void onStarted(String personaId) {
                sink.onPersona(personaId);
            }

            @Override
            public void onToken(String textDelta, String personaId) {
                // Late safety net if 'started' was skipped — capture the persona from a token too.
                sink.onPersona(personaId);
                sink.onToken(textDelta);
            }

            @Override
            public void onToolUse(String name, String inputJson, boolean terminal) {
                sink.onToolUse(name, inputJson);
            }

            @Override
            public void onDone() {
                sink.onDone();
            }

            @Override
            public void onError(String userSafeMessage) {
                sink.onError(userSafeMessage);
            }
        });
    }

    private static List<ConvTurn> mapHistory(List<VoiceSession.Utterance> history) {
        if (history == null) {
            return List.of();
        }
        return history.stream()
            .map(u -> new ConvTurn(u.role(), u.text(), u.personaId()))
            .toList();
    }

    /** The user's recent repos as ConverseTurn grounding (recent-first, capped by the service). */
    private List<ConvRepoRef> mapRepos(String userId) {
        return userRepoService.recentRepos(userId, UserRepoService.DEFAULT_GROUNDING_LIMIT).stream()
            .map(r -> new ConvRepoRef(r.getOwner(), r.getName(), r.getRepoUrl(),
                                      r.getKind() == null ? "" : r.getKind().name()))
            .toList();
    }

    /**
     * The user's in-flight pipelines (RUNNING/PENDING) as ConverseTurn grounding, recent-first
     * and capped. Read from the {@code pipeline_runs} projection (kept live by arbiter callbacks),
     * so the persona always knows the current build status. Best-effort: never break a turn.
     */
    private List<ConvPipelineRef> mapPipelines(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        try {
            return pipelineRunRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(ArbiterConversationEngine::isInFlight)
                .limit(MAX_PIPELINE_GROUNDING)
                .map(ArbiterConversationEngine::toPipelineRef)
                .toList();
        } catch (Exception e) {
            // Grounding is a nicety — a projection read failure must not fail the conversation.
            return List.of();
        }
    }

    private static boolean isInFlight(PipelineRun run) {
        PipelineStatus s = run.getStatus();
        return s == PipelineStatus.RUNNING || s == PipelineStatus.PENDING;
    }

    private static ConvPipelineRef toPipelineRef(PipelineRun run) {
        String name = (run.getName() != null && !run.getName().isBlank()) ? run.getName() : run.getPlaybook();
        String blocked = run.getBlockedCheckpointId();
        // The projection keeps status RUNNING while parked on a gate; surface BLOCKED so the
        // persona (and the arbiter's session-status derivation) treat an open gate correctly.
        String status = (blocked != null && !blocked.isBlank()) ? "BLOCKED"
            : (run.getStatus() == null ? "" : run.getStatus().name());
        return new ConvPipelineRef(
            run.getId(),
            name == null ? "" : name,
            status,
            currentRole(run),
            blocked == null ? "" : blocked);
    }

    /** The role currently working: the first role across phases whose state is RUNNING; else blank. */
    private static String currentRole(PipelineRun run) {
        Map<String, PhaseState> phases = run.getPhases();
        if (phases == null) {
            return "";
        }
        for (PhaseState phase : phases.values()) {
            if (phase == null || phase.getRoles() == null) {
                continue;
            }
            for (Map.Entry<String, RoleState> e : phase.getRoles().entrySet()) {
                RoleState rs = e.getValue();
                if (rs != null && "RUNNING".equals(rs.getStatus())) {
                    return e.getKey();
                }
            }
        }
        return "";
    }
}
