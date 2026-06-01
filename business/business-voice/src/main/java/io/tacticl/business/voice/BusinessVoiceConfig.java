package io.tacticl.business.voice;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the voice command-center business layer. Everything here is gated
 * behind {@code tacticl.voice.enabled=true}; with the flag off no voice bean is
 * created, so the module is inert in environments that have not provisioned
 * Deepgram/ElevenLabs.
 *
 * <p>Imported hierarchically by the application entry point alongside the
 * client-deepgram / client-elevenlabs configs it depends on — no broad
 * component scanning.
 */
@Configuration
@ConditionalOnProperty(name = "tacticl.voice.enabled", havingValue = "true")
public class BusinessVoiceConfig {

    @Bean
    @ConfigurationProperties(prefix = "tacticl.voice")
    public VoiceProperties voiceProperties() {
        return new VoiceProperties();
    }
}
