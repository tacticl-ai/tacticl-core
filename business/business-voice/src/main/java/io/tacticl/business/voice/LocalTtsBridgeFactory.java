package io.tacticl.business.voice;

import java.net.http.HttpClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds a fresh {@link LocalTtsBridge} per voice session, pointed at the local
 * voice sidecar's base URL ({@code tacticl.voice.local-base-url}). The
 * {@link HttpClient} and {@link JsonMapper} are shared singletons (each bridge
 * opens a short-lived WS per utterance).
 *
 * <p>Not a {@code @Component}: {@link BusinessVoiceConfig} constructs this and
 * exposes it as the active {@link TtsBridgeFactory} bean when
 * {@code tacticl.voice.tts-provider=local}.
 */
public class LocalTtsBridgeFactory implements TtsBridgeFactory {

    private final HttpClient httpClient;

    private final JsonMapper mapper;

    private final String baseUrl;

    public LocalTtsBridgeFactory(HttpClient httpClient, JsonMapper mapper, String baseUrl) {
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.baseUrl = baseUrl;
    }

    /**
     * @param voiceId optional explicit voice id; {@code null} uses the sidecar default.
     */
    @Override
    public TtsBridge create(String voiceId) {
        return new LocalTtsBridge(httpClient, mapper, baseUrl, voiceId);
    }
}
