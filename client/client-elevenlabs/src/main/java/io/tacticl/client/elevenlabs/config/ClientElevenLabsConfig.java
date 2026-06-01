package io.tacticl.client.elevenlabs.config;

import io.tacticl.client.elevenlabs.client.ElevenLabsClient;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates ElevenLabs streaming TTS client beans. */
@Configuration
@ConditionalOnProperty(name = "tacticl.elevenlabs.enabled", havingValue = "true")
public class ClientElevenLabsConfig {

    @Bean
    @ConfigurationProperties(prefix = "tacticl.elevenlabs")
    public ElevenLabsConfig elevenLabsConfig() {
        return new ElevenLabsConfig();
    }

    @Bean
    public HttpClient elevenLabsHttpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    @Bean
    public ElevenLabsClient elevenLabsClient(ElevenLabsConfig config, HttpClient elevenLabsHttpClient) {
        return new ElevenLabsClient(config, elevenLabsHttpClient);
    }

}
