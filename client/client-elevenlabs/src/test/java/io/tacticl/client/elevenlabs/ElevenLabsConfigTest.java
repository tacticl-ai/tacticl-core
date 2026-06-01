package io.tacticl.client.elevenlabs;

import io.tacticl.client.elevenlabs.config.ClientElevenLabsConfig;
import io.tacticl.client.elevenlabs.config.ElevenLabsConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ElevenLabsConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
        .withUserConfiguration(ClientElevenLabsConfig.class);

    @Test
    void disabledByDefault() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(ElevenLabsConfig.class));
    }

    @Test
    void bindsTacticlElevenLabsPropertyPrefix() {
        runner.withPropertyValues(
            "tacticl.elevenlabs.enabled=true",
            "tacticl.elevenlabs.api-base-url=wss://test.elevenlabs.io",
            "tacticl.elevenlabs.model=eleven_test_model",
            "tacticl.elevenlabs.default-voice-id=adam-real-id",
            "tacticl.elevenlabs.default-output-format=pcm_16000"
        ).run(ctx -> {
            assertThat(ctx).hasSingleBean(ElevenLabsConfig.class);
            ElevenLabsConfig cfg = ctx.getBean(ElevenLabsConfig.class);
            assertThat(cfg.getApiBaseUrl()).isEqualTo("wss://test.elevenlabs.io");
            assertThat(cfg.getModel()).isEqualTo("eleven_test_model");
            assertThat(cfg.getDefaultVoiceId()).isEqualTo("adam-real-id");
            assertThat(cfg.getDefaultOutputFormat()).isEqualTo("pcm_16000");
            assertThat(cfg.isConfigured()).isFalse(); // no api key set yet
        });
    }

    @Test
    void defaultsMatchSadDefaults() {
        runner.withPropertyValues(
            "tacticl.elevenlabs.enabled=true"
        ).run(ctx -> {
            ElevenLabsConfig cfg = ctx.getBean(ElevenLabsConfig.class);
            assertThat(cfg.getApiBaseUrl()).isEqualTo("wss://api.elevenlabs.io");
            assertThat(cfg.getModel()).isEqualTo("eleven_turbo_v2");
            assertThat(cfg.getDefaultOutputFormat()).isEqualTo("mp3_44100_128");
            assertThat(cfg.getDefaultVoiceId()).isEqualTo("adam-stub-voice-id");
        });
    }

    @Test
    void isConfiguredTrueWhenKeySet() {
        ElevenLabsConfig cfg = new ElevenLabsConfig();
        assertThat(cfg.isConfigured()).isFalse();
        cfg.setApiKey("xi-key");
        assertThat(cfg.isConfigured()).isTrue();
    }

}
