package io.tacticl.business.voice;

import io.tacticl.client.elevenlabs.client.ElevenLabsClient;

/**
 * Builds a fresh {@link ElevenLabsTtsBridge} per voice session. The
 * {@link ElevenLabsClient} is a stateless singleton; each bridge manages its own
 * short-lived per-utterance sessions, so the service needs a factory rather than
 * a shared instance. Trivially overridable in tests with a fake.
 *
 * <p>Not a {@code @Component}: {@link BusinessVoiceConfig} constructs this (or the
 * local factory) and exposes the selected one as the single active
 * {@link TtsBridgeFactory} bean per {@code tacticl.voice.tts-provider}.
 */
public class ElevenLabsTtsBridgeFactory implements TtsBridgeFactory {

    private final ElevenLabsClient client;

    public ElevenLabsTtsBridgeFactory(ElevenLabsClient client) {
        this.client = client;
    }

    /**
     * @param voiceId optional explicit voice id; {@code null} uses the client default.
     */
    @Override
    public TtsBridge create(String voiceId) {
        return new ElevenLabsTtsBridge(client, voiceId);
    }
}
