package io.tacticl.business.voice;

import java.net.http.HttpClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds a fresh {@link LocalSttBridge} per voice session, pointed at the local
 * voice sidecar's base URL ({@code tacticl.voice.local-base-url}). The
 * {@link HttpClient} and {@link JsonMapper} are shared singletons (the WS sessions
 * the bridges create are per-turn).
 *
 * <p>Not a {@code @Component}: {@link BusinessVoiceConfig} constructs this and
 * exposes it as the active {@link SttBridgeFactory} bean when
 * {@code tacticl.voice.stt-provider=local}.
 */
public class LocalSttBridgeFactory implements SttBridgeFactory {

    private final HttpClient httpClient;

    private final JsonMapper mapper;

    private final String baseUrl;

    public LocalSttBridgeFactory(HttpClient httpClient, JsonMapper mapper, String baseUrl) {
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.baseUrl = baseUrl;
    }

    @Override
    public SttBridge create() {
        return new LocalSttBridge(httpClient, mapper, baseUrl);
    }
}
