package io.tacticl.business.voice;

/**
 * Bound configuration for the voice command center (prefix {@code tacticl.voice}).
 * Created and bound in {@link BusinessVoiceConfig}.
 *
 * <ul>
 *   <li>{@code enabled} — gates every voice bean ({@code @ConditionalOnProperty}).</li>
 *   <li>{@code voiceId} — optional ElevenLabs voice id; {@code null}/blank falls
 *       back to the client's configured default voice.</li>
 * </ul>
 */
public class VoiceProperties {

    private boolean enabled;

    private String voiceId;

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
}
