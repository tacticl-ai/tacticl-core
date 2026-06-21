package io.tacticl.business.voice;

/**
 * Builds a fresh {@link SttBridge} per voice session. Each bridge owns one
 * streaming session, so {@link VoiceSessionService} needs a factory rather than a
 * shared instance. The concrete factory ({@link DeepgramSttBridgeFactory} or
 * {@link LocalSttBridgeFactory}) is selected by {@code tacticl.voice.stt-provider}
 * and exposed as the active {@code SttBridgeFactory} bean in
 * {@link BusinessVoiceConfig}.
 */
public interface SttBridgeFactory {

    SttBridge create();
}
