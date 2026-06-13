package io.tacticl.service.voice.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.voice.VoiceConversationStore;
import io.tacticl.business.voice.VoiceConversationStore.ConversationSummary;
import io.tacticl.business.voice.VoiceConversationStore.ConversationTranscript;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API for the voice conversation picker. Lists a user's durable voice
 * conversations (most-recent first) and returns one conversation's full
 * transcript so the client can render it before resuming via
 * {@code wss://…/v1/voice?token=…&cid=<id>}.
 *
 * <p>Writes happen on the voice WebSocket turn loop (durable write-through in
 * {@code VoiceSessionService}/{@code VoiceConversationStore}), not here — this
 * controller is intentionally read-only. Ownership is enforced in the store via
 * {@code findByIdAndUserId}. Gated by {@code tacticl.voice.enabled=true}.
 */
@RestController
@RequestMapping("/v1/voice/conversations")
@ConditionalOnProperty(name = "tacticl.voice.enabled", havingValue = "true")
public class VoiceConversationController extends BaseController {

    private final VoiceConversationStore store;

    public VoiceConversationController(VoiceConversationStore store) {
        this.store = store;
    }

    @Override
    protected String getModuleName() {
        return "voice-conversation";
    }

    /** List the caller's voice conversations, most-recently-updated first. */
    @GetMapping
    @RequireAuth
    public ResponseEntity<List<ConversationSummary>> list(@AuthUser AuthenticatedUser user) {
        return ResponseEntity.ok(store.listSummaries(user.getUserId()));
    }

    /** Full transcript of one conversation the caller owns; 404 if unknown. */
    @GetMapping("/{conversationId}")
    @RequireAuth
    public ResponseEntity<ConversationTranscript> get(@PathVariable String conversationId,
                                                      @AuthUser AuthenticatedUser user) {
        return store.transcript(conversationId, user.getUserId())
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
