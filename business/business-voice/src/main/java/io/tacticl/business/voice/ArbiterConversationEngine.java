package io.tacticl.business.voice;

import io.tacticl.client.arbiter.conversation.ConvTurn;
import io.tacticl.client.arbiter.conversation.ConverseEventListener;
import io.tacticl.client.arbiter.conversation.ConverseTurnInput;
import io.tacticl.client.arbiter.conversation.ConversationServiceClient;
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

    public ArbiterConversationEngine(ConversationServiceClient client, VoiceProperties properties) {
        this.client = client;
        this.properties = properties;
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
            /* locale */ null);

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
}
