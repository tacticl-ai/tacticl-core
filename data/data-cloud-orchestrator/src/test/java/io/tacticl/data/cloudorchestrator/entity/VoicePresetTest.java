package io.tacticl.data.cloudorchestrator.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class VoicePresetTest {

    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void serializationRoundTrip_preservesAllFields() throws Exception {
        VoicePreset preset = new VoicePreset("adam", "calm", 0.5, 0.75);
        String json = mapper.writeValueAsString(preset);
        VoicePreset round = mapper.readValue(json, VoicePreset.class);
        assertThat(round.getProviderVoiceId()).isEqualTo("adam");
        assertThat(round.getStyle()).isEqualTo("calm");
        assertThat(round.getStability()).isEqualTo(0.5);
        assertThat(round.getSimilarityBoost()).isEqualTo(0.75);
    }
}
