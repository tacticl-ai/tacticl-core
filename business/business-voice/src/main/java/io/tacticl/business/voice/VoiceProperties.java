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
 *   <li>{@code sttProvider} — which STT bridge to use ({@code deepgram} | {@code local});
 *       defaults to {@code deepgram} so existing behavior is unchanged.</li>
 *   <li>{@code ttsProvider} — which TTS bridge to use ({@code elevenlabs} | {@code local});
 *       defaults to {@code elevenlabs} so existing behavior is unchanged.</li>
 *   <li>{@code local.baseUrl} — base WS URL of the local voice sidecar when either
 *       provider is {@code local}.</li>
 * </ul>
 */
public class VoiceProperties {

    /** Default conversation model — Haiku for sub-second first-token on voice turns. */
    private static final String DEFAULT_CONVERSATION_MODEL = "claude-haiku-4-5-20251001";

    /** Default product id — this backend is the tacticl product. */
    private static final String DEFAULT_PRODUCT_ID = "tacticl";

    /** Provider token selecting the managed STT path (the default). */
    public static final String STT_DEEPGRAM = "deepgram";

    /** Provider token selecting the managed TTS path (the default). */
    public static final String TTS_ELEVENLABS = "elevenlabs";

    /** Provider token selecting the local-sidecar path (STT or TTS). */
    public static final String PROVIDER_LOCAL = "local";

    private boolean enabled;

    private String voiceId;

    private String conversationModel = DEFAULT_CONVERSATION_MODEL;

    private String productId = DEFAULT_PRODUCT_ID;

    /** STT provider: {@code deepgram} (default) | {@code local}. */
    private String sttProvider = STT_DEEPGRAM;

    /** TTS provider: {@code elevenlabs} (default) | {@code local}. */
    private String ttsProvider = TTS_ELEVENLABS;

    /** Nested local-sidecar config (base WS URL); used when a provider is {@code local}. */
    private Local local = new Local();

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

    /** STT provider token; never blank (falls back to {@code deepgram}). */
    public String getSttProvider() {
        return (sttProvider == null || sttProvider.isBlank()) ? STT_DEEPGRAM : sttProvider;
    }

    public void setSttProvider(String sttProvider) {
        this.sttProvider = sttProvider;
    }

    /** TTS provider token; never blank (falls back to {@code elevenlabs}). */
    public String getTtsProvider() {
        return (ttsProvider == null || ttsProvider.isBlank()) ? TTS_ELEVENLABS : ttsProvider;
    }

    public void setTtsProvider(String ttsProvider) {
        this.ttsProvider = ttsProvider;
    }

    public Local getLocal() {
        return local;
    }

    public void setLocal(Local local) {
        this.local = local != null ? local : new Local();
    }

    /** True when the configured STT provider is the local sidecar. */
    public boolean isLocalStt() {
        return PROVIDER_LOCAL.equalsIgnoreCase(getSttProvider());
    }

    /** True when the configured TTS provider is the local sidecar. */
    public boolean isLocalTts() {
        return PROVIDER_LOCAL.equalsIgnoreCase(getTtsProvider());
    }

    /**
     * Nested config for the local voice sidecar (bound under {@code tacticl.voice.local}).
     * Only consulted when {@code stt-provider} or {@code tts-provider} is {@code local}.
     */
    public static class Local {

        /** Default sidecar base WS URL — the compose service name + port. */
        private static final String DEFAULT_BASE_URL = "ws://voice-sidecar:8700";

        private String baseUrl = DEFAULT_BASE_URL;

        /** Base WS URL of the local voice sidecar; never blank (falls back to the default). */
        public String getBaseUrl() {
            return (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
