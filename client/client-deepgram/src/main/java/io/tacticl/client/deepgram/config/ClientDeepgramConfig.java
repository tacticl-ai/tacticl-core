package io.tacticl.client.deepgram.config;

import io.tacticl.client.deepgram.client.DeepgramClient;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates Deepgram client beans when {@code tacticl.deepgram.enabled=true}.
 *
 * <p>Provides a shared {@link HttpClient} for WebSocket upgrades. The
 * {@link DeepgramClient} bean opens one {@link java.net.http.WebSocket} per
 * voice session via this shared HttpClient.
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.deepgram.enabled", havingValue = "true")
public class ClientDeepgramConfig {

    @Bean
    @ConfigurationProperties(prefix = "tacticl.deepgram")
    public DeepgramConfig deepgramConfig() {
        return new DeepgramConfig();
    }

    @Bean
    public HttpClient deepgramHttpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Bean
    public DeepgramClient deepgramClient(DeepgramConfig config, HttpClient deepgramHttpClient) {
        return new DeepgramClient(config, deepgramHttpClient);
    }

}
