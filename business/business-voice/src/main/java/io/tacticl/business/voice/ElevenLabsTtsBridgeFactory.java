package io.tacticl.business.voice;

import io.tacticl.client.elevenlabs.client.ElevenLabsClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Builds a fresh {@link ElevenLabsTtsBridge} per voice session. The
 * {@link ElevenLabsClient} is a stateless singleton; each bridge manages its own
 * short-lived per-utterance sessions, so the service needs a factory rather than
 * a shared instance. Trivially overridable in tests with a fake.
 */
@Component
@ConditionalOnProperty(name = "tacticl.voice.enabled", havingValue = "true")
public class ElevenLabsTtsBridgeFactory {

    private final ElevenLabsClient client;

    public ElevenLabsTtsBridgeFactory(ElevenLabsClient client) {
        this.client = client;
    }

    /**
     * @param voiceId optional explicit voice id; {@code null} uses the client default.
     */
    public ElevenLabsTtsBridge create(String voiceId) {
        return new ElevenLabsTtsBridge(client, voiceId);
    }
}
