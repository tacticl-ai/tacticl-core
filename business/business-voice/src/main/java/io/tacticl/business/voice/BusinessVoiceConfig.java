package io.tacticl.business.voice;

import io.tacticl.client.deepgram.client.DeepgramClient;
import io.tacticl.client.elevenlabs.client.ElevenLabsClient;
import java.net.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wiring for the voice command-center business layer. Everything here is gated
 * behind {@code tacticl.voice.enabled=true}; with the flag off no voice bean is
 * created, so the module is inert in environments that have not provisioned a
 * voice provider.
 *
 * <p>Provider selection: this config exposes the single active
 * {@link SttBridgeFactory} + {@link TtsBridgeFactory} beans, chosen from
 * {@link VoiceProperties} ({@code stt-provider} / {@code tts-provider}). The
 * default ({@code deepgram} + {@code elevenlabs}) reproduces the prior behavior
 * exactly; switching either to {@code local} routes that leg to the local sidecar
 * over WebSocket. {@link VoiceSessionService} injects the interfaces, never the
 * concrete bridges, so neither path leaks into the orchestration.
 *
 * <p>The managed clients ({@code DeepgramClient} / {@code ElevenLabsClient}) are
 * injected via {@link ObjectProvider} so a local-only deployment that has not
 * enabled {@code tacticl.deepgram} / {@code tacticl.elevenlabs} still wires
 * cleanly — the managed client is only dereferenced when its provider is selected.
 *
 * <p>Imported hierarchically by the application entry point alongside the
 * client-deepgram / client-elevenlabs configs it depends on — no broad
 * component scanning.
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.voice.enabled", havingValue = "true")
public class BusinessVoiceConfig {

    private static final Logger log = LoggerFactory.getLogger(BusinessVoiceConfig.class);

    @Bean
    @ConfigurationProperties(prefix = "tacticl.voice")
    public VoiceProperties voiceProperties() {
        return new VoiceProperties();
    }

    /**
     * Shared JDK HttpClient for the local sidecar WS legs. A single instance backs
     * every per-turn WebSocket the local bridges open (its internal executor runs the
     * inbound listener callbacks). Harmless to create even when no provider is local.
     */
    @Bean
    public HttpClient voiceLocalHttpClient() {
        return HttpClient.newHttpClient();
    }

    /** Lenient JsonMapper for the local sidecar's small JSON control frames. */
    @Bean
    public JsonMapper voiceLocalJsonMapper() {
        return JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    }

    /**
     * The active STT bridge factory: {@link LocalSttBridgeFactory} when
     * {@code tacticl.voice.stt-provider=local}, else {@link DeepgramSttBridgeFactory}.
     */
    @Bean
    public SttBridgeFactory sttBridgeFactory(VoiceProperties properties,
                                             ObjectProvider<DeepgramClient> deepgramClient,
                                             HttpClient voiceLocalHttpClient,
                                             JsonMapper voiceLocalJsonMapper) {
        if (properties.isLocalStt()) {
            log.info("Voice STT provider = local (sidecar {})", properties.getLocal().getBaseUrl());
            return new LocalSttBridgeFactory(voiceLocalHttpClient, voiceLocalJsonMapper,
                properties.getLocal().getBaseUrl());
        }
        DeepgramClient client = deepgramClient.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException(
                "tacticl.voice.stt-provider=deepgram requires tacticl.deepgram.enabled=true "
                    + "(no DeepgramClient bean present)");
        }
        log.info("Voice STT provider = deepgram");
        return new DeepgramSttBridgeFactory(client);
    }

    /**
     * The active TTS bridge factory: {@link LocalTtsBridgeFactory} when
     * {@code tacticl.voice.tts-provider=local}, else {@link ElevenLabsTtsBridgeFactory}.
     */
    @Bean
    public TtsBridgeFactory ttsBridgeFactory(VoiceProperties properties,
                                             ObjectProvider<ElevenLabsClient> elevenLabsClient,
                                             HttpClient voiceLocalHttpClient,
                                             JsonMapper voiceLocalJsonMapper) {
        if (properties.isLocalTts()) {
            log.info("Voice TTS provider = local (sidecar {})", properties.getLocal().getBaseUrl());
            return new LocalTtsBridgeFactory(voiceLocalHttpClient, voiceLocalJsonMapper,
                properties.getLocal().getBaseUrl());
        }
        ElevenLabsClient client = elevenLabsClient.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException(
                "tacticl.voice.tts-provider=elevenlabs requires tacticl.elevenlabs.enabled=true "
                    + "(no ElevenLabsClient bean present)");
        }
        log.info("Voice TTS provider = elevenlabs");
        return new ElevenLabsTtsBridgeFactory(client);
    }
}
