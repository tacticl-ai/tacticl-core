package io.tacticl.business.voice;

import io.strategiz.social.client.github.config.GitHubConfig;
import io.tacticl.business.profile.service.UserRepoService;
import io.tacticl.client.arbiter.conversation.ConvRepoRef;
import io.tacticl.client.arbiter.conversation.ConvTurn;
import io.tacticl.client.arbiter.conversation.ConverseEventListener;
import io.tacticl.client.arbiter.conversation.ConverseTurnInput;
import io.tacticl.client.arbiter.conversation.ConversationServiceClient;
import io.tacticl.data.profile.entity.UserRepo;
import java.util.List;
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

    public ArbiterConversationEngine(ConversationServiceClient client,
                                     VoiceProperties properties,
                                     GitHubConfig gitHubConfig,
                                     UserRepoService userRepoService) {
        this.client = client;
        this.properties = properties;
        this.gitHubConfig = gitHubConfig;
        this.userRepoService = userRepoService;
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
            mapRepos(ctx.userId()));

        client.converseTurn(input, new ConverseEventListener() {
            @Override
            public void onToken(String textDelta, String personaId) {
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
            .map(u -> new ConvTurn(u.role(), u.text(), /* personaId */ null))
            .toList();
    }

    /** The user's recent repos as ConverseTurn grounding (recent-first, capped by the service). */
    private List<ConvRepoRef> mapRepos(String userId) {
        return userRepoService.recentRepos(userId, UserRepoService.DEFAULT_GROUNDING_LIMIT).stream()
            .map(r -> new ConvRepoRef(r.getOwner(), r.getName(), r.getRepoUrl(),
                                      r.getKind() == null ? "" : r.getKind().name()))
            .toList();
    }
}
