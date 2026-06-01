package io.tacticl.business.voice;

import io.tacticl.client.deepgram.client.DeepgramClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Builds a fresh {@link DeepgramSttBridge} per voice session. The
 * {@link DeepgramClient} is a stateless singleton; each bridge owns one
 * streaming session, so the service needs a factory rather than a shared
 * instance. Trivially overridable in tests with a fake that returns a stub bridge.
 */
@Component
@ConditionalOnProperty(name = "tacticl.voice.enabled", havingValue = "true")
public class DeepgramSttBridgeFactory {

    private final DeepgramClient client;

    public DeepgramSttBridgeFactory(DeepgramClient client) {
        this.client = client;
    }

    public DeepgramSttBridge create() {
        return new DeepgramSttBridge(client);
    }
}
