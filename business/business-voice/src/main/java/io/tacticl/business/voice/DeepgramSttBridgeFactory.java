package io.tacticl.business.voice;

import io.tacticl.client.deepgram.client.DeepgramClient;

/**
 * Builds a fresh {@link DeepgramSttBridge} per voice session. The
 * {@link DeepgramClient} is a stateless singleton; each bridge owns one
 * streaming session, so the service needs a factory rather than a shared
 * instance. Trivially overridable in tests with a fake that returns a stub bridge.
 *
 * <p>Not a {@code @Component}: {@link BusinessVoiceConfig} constructs this (or the
 * local factory) and exposes the selected one as the single active
 * {@link SttBridgeFactory} bean per {@code tacticl.voice.stt-provider}.
 */
public class DeepgramSttBridgeFactory implements SttBridgeFactory {

    private final DeepgramClient client;

    public DeepgramSttBridgeFactory(DeepgramClient client) {
        this.client = client;
    }

    @Override
    public SttBridge create() {
        return new DeepgramSttBridge(client);
    }
}
