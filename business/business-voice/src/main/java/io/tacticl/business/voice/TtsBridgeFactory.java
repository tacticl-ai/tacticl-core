package io.tacticl.business.voice;

/**
 * Builds a fresh {@link TtsBridge} per voice session. Each bridge manages its own
 * short-lived per-utterance sessions, so {@link VoiceSessionService} needs a
 * factory rather than a shared instance. The concrete factory
 * ({@link ElevenLabsTtsBridgeFactory} or {@link LocalTtsBridgeFactory}) is selected
 * by {@code tacticl.voice.tts-provider} and exposed as the active
 * {@code TtsBridgeFactory} bean in {@link BusinessVoiceConfig}.
 */
public interface TtsBridgeFactory {

    /**
     * @param voiceId optional explicit voice id; {@code null} uses the provider default.
     */
    TtsBridge create(String voiceId);
}
