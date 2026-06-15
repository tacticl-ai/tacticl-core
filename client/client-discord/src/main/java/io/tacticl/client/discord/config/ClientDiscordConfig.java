package io.tacticl.client.discord.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.tacticl.client.discord.DiscordEd25519Verifier;
import io.tacticl.client.discord.DiscordRestClient;
import io.tacticl.client.discord.gateway.DiscordGatewayClient;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Discord client beans. Dormant by default: only active when
 * {@code tacticl.discord.enabled=true}.
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.discord.enabled", havingValue = "true")
public class ClientDiscordConfig {

    @Bean
    @ConfigurationProperties(prefix = "tacticl.discord")
    public DiscordConfig discordConfig() {
        return new DiscordConfig();
    }

    @Bean
    public Bucket discordApiBucket(DiscordConfig config) {
        Bandwidth limit = Bandwidth.classic(
            config.getRateLimitPerMinute(),
            Refill.intervally(config.getRateLimitPerMinute(), Duration.ofMinutes(1))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    @Bean
    public DiscordRestClient discordRestClient(DiscordConfig config, Bucket discordApiBucket) {
        return new DiscordRestClient(config, discordApiBucket);
    }

    @Bean
    public DiscordEd25519Verifier discordEd25519Verifier(DiscordConfig config) {
        return new DiscordEd25519Verifier(config);
    }

    /**
     * The Gateway client for free-form messages. Gated on the {@code tacticl.discord.gateway-enabled}
     * sub-flag so the WebSocket can be rolled independently of the REST/interactions path. The
     * business layer ({@code DiscordGatewayBridge}) sets the listener and drives start/shutdown.
     */
    @Bean
    @ConditionalOnProperty(name = "tacticl.discord.gateway-enabled", havingValue = "true")
    public DiscordGatewayClient discordGatewayClient(DiscordConfig config) {
        return new DiscordGatewayClient(config, HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build());
    }
}

