package io.tacticl.business.voice;

import io.cidadel.client.anthropic.AnthropicDirectClient;
import io.cidadel.client.base.llm.model.LlmMessage;
import io.cidadel.client.base.llm.model.LlmResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * TRANSITION-ONLY fallback {@link ConversationEngine}: an in‑JVM Anthropic call.
 * Active only when the arbiter conversation brain is NOT enabled
 * ({@code tacticl.voice.arbiter-conversation.enabled} false/unset) — i.e. local/dev
 * and as a known‑good path while the arbiter brain is being proven in QA→prod.
 *
 * <p><b>Slated for deletion.</b> The arbiter is the sole brain in the end state
 * (migrate plan §1.1 explicitly retires this "parallel LLM path that doesn't
 * inherit Arbiter's multi-provider fallback"). DELETE this class — and the
 * duplicated {@code /voice-conversation/system-prompt.md} it loads — once the
 * arbiter ConverseTurn brain is live and proven in prod; on arbiter outage the
 * voice plane should degrade to a spoken "trouble" message, not a second LLM.
 * Until then, keep this prompt aligned with the arbiter persona to limit drift.
 *
 * <p>Non‑streaming: it generates the whole reply, then emits it as a single token
 * before {@code onDone}. The handler buffers either way, so behaviour matches the
 * pre‑seam {@code VoiceConversationTurnHandler}.
 */
@Service
@ConditionalOnProperty(name = "tacticl.voice.arbiter-conversation.enabled",
                       havingValue = "false", matchIfMissing = true)
public class AnthropicDirectConversationEngine implements ConversationEngine {

    private static final Logger log = LoggerFactory.getLogger(AnthropicDirectConversationEngine.class);

    private static final String SYSTEM_PROMPT_RESOURCE = "/voice-conversation/system-prompt.md";

    private static final String FALLBACK_REPLY = "Sorry — I didn't catch that. Could you say it again?";

    private final AnthropicDirectClient anthropicClient;

    private final VoiceProperties properties;

    private final String systemPrompt;

    public AnthropicDirectConversationEngine(AnthropicDirectClient anthropicClient,
                                             VoiceProperties properties) {
        this.anthropicClient = anthropicClient;
        this.properties = properties;
        this.systemPrompt = loadSystemPrompt();
    }

    @Override
    public void converse(ConversationContext ctx, ConversationSink sink) {
        try {
            // AnthropicDirectClient has no native system slot — fold the persona prompt
            // into a leading user→assistant pair, then replay prior turns.
            List<LlmMessage> history = new ArrayList<>();
            history.add(LlmMessage.user("System instructions:\n" + systemPrompt));
            history.add(LlmMessage.assistant("Understood. I'll follow those instructions."));
            if (ctx.history() != null) {
                for (VoiceSession.Utterance turn : ctx.history()) {
                    history.add("assistant".equals(turn.role())
                        ? LlmMessage.assistant(turn.text())
                        : LlmMessage.user(turn.text()));
                }
            }

            LlmResponse response = anthropicClient.generateContent(
                ctx.userText(), history, properties.getConversationModel());
            String content = response != null ? response.getContent() : null;
            String reply = (content == null || content.isBlank()) ? FALLBACK_REPLY : content.trim();

            sink.onToken(reply);
            sink.onDone();
        } catch (Exception e) {
            log.warn("In-JVM conversation turn failed session={}: {}", ctx.sessionId(), e.toString());
            sink.onError("Sorry — I hit a snag answering that. Try me again.");
        }
    }

    private String loadSystemPrompt() {
        try (InputStream in = getClass().getResourceAsStream(SYSTEM_PROMPT_RESOURCE)) {
            if (in != null) {
                String prompt = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (!prompt.isBlank()) {
                    return prompt;
                }
            }
            log.warn("Voice conversation system prompt {} missing/empty — using a minimal fallback",
                     SYSTEM_PROMPT_RESOURCE);
        } catch (IOException e) {
            log.warn("Failed to read {} — using a minimal fallback: {}", SYSTEM_PROMPT_RESOURCE, e.toString());
        }
        return "You are Tacticl, a concise, direct spoken voice assistant. Reply in one to "
            + "three plain spoken sentences — no markdown, no lists. Ask one question at a time.";
    }
}
