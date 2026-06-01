package io.tacticl.service.voice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Wiring for the voice command-center service (transport) layer.
 *
 * <p>Binds {@link VoiceTransportProperties} from {@code tacticl.voice.*} and
 * imports {@link VoiceTransportConfig} (the WebSocket registration). Both this
 * and the {@code BusinessVoiceConfig} bind under the same {@code tacticl.voice}
 * prefix to disjoint field sets — Spring's default unknown-field tolerance keeps
 * them independent (business owns {@code enabled}/{@code voiceId}; transport owns
 * {@code ws-path}/{@code token-ttl-seconds}/{@code public-ws-url}).
 *
 * <p>The token service, controller, and WS handler are picked up by component
 * scan of {@code io.tacticl} from the application entry point; everything is
 * gated by {@code tacticl.voice.enabled=true} so the service layer is inert when
 * voice is dormant. Imported hierarchically — no broad component scan added here.
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.voice.enabled", havingValue = "true")
@Import(VoiceTransportConfig.class)
public class ServiceVoiceConfig {

    @Bean
    @ConfigurationProperties(prefix = "tacticl.voice")
    public VoiceTransportProperties voiceTransportProperties() {
        return new VoiceTransportProperties();
    }
}
