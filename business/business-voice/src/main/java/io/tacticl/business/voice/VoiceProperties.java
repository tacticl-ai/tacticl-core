package io.tacticl.business.voice;

/**
 * Bound configuration for the voice command center (prefix {@code tacticl.voice}).
 * Created and bound in {@link BusinessVoiceConfig}.
 *
 * <ul>
 *   <li>{@code enabled} — gates every voice bean ({@code @ConditionalOnProperty}).</li>
 *   <li>{@code voiceId} — optional ElevenLabs voice id; {@code null}/blank falls
 *       back to the client's configured default voice.</li>
 *   <li>{@code conversationModel} — Anthropic model id the conversation persona
 *       speaks with (Haiku by default for low voice latency).</li>
 * </ul>
 */
public class VoiceProperties {

    /** Default conversation model — Haiku for sub-second first-token on voice turns. */
    private static final String DEFAULT_CONVERSATION_MODEL = "claude-haiku-4-5-20251001";

    /** Default product id — this backend is the tacticl product. */
    private static final String DEFAULT_PRODUCT_ID = "tacticl";

    private boolean enabled;

    private String voiceId;

    private String conversationModel = DEFAULT_CONVERSATION_MODEL;

    private String productId = DEFAULT_PRODUCT_ID;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Optional ElevenLabs voice id; null when unset so the client default applies. */
    public String getVoiceId() {
        return (voiceId == null || voiceId.isBlank()) ? null : voiceId;
    }

    public void setVoiceId(String voiceId) {
        this.voiceId = voiceId;
    }

    /** Anthropic model the conversation persona speaks with; never blank (falls back to the default). */
    public String getConversationModel() {
        return (conversationModel == null || conversationModel.isBlank())
            ? DEFAULT_CONVERSATION_MODEL : conversationModel;
    }

    public void setConversationModel(String conversationModel) {
        this.conversationModel = conversationModel;
    }

    /** Product id scoping conversation turns (sent to the arbiter); never blank. */
    public String getProductId() {
        return (productId == null || productId.isBlank()) ? DEFAULT_PRODUCT_ID : productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }
}
