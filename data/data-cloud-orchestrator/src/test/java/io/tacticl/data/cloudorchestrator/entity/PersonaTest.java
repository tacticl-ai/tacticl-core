package io.tacticl.data.cloudorchestrator.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PersonaTest {

    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void create_setsDefaults() {
        Persona p = Persona.create("product-manager", PersonaFamily.CONVERSATIONAL,
                "Product Manager", "PM persona", "system prompt body",
                "claude-sonnet-4-6",
                List.of("ask_clarification", "propose_implementation"),
                new VoicePreset("adam", "calm", 0.5, 0.75));

        assertThat(p.getId()).isEqualTo("product-manager");
        assertThat(p.getFamily()).isEqualTo(PersonaFamily.CONVERSATIONAL);
        assertThat(p.isActive()).isTrue();
        assertThat(p.getVersion()).isEqualTo(1);
        assertThat(p.getSkillIds()).containsExactly("ask_clarification", "propose_implementation");
        assertThat(p.getVoicePreset()).isNotNull();
        assertThat(p.getCreatedAt()).isNotNull();
    }

    @Test
    void bumpVersion_incrementsAndTouchesUpdatedAt() {
        Persona p = Persona.create("architect", PersonaFamily.PDLC,
                "Architect", "desc", "prompt", "claude-haiku-4-5",
                List.of("read", "write"), null);
        int before = p.getVersion();
        p.bumpVersion();
        assertThat(p.getVersion()).isEqualTo(before + 1);
    }

    @Test
    void serializationRoundTrip_preservesAllFields() throws Exception {
        Persona p = Persona.create("market-researcher", PersonaFamily.CONVERSATIONAL,
                "Market Researcher", "Skeptical evidence-driven", "system",
                "claude-sonnet-4-6",
                List.of("web_search", "read_page"),
                new VoicePreset("adam", "calm", 0.5, 0.75));

        String json = mapper.writeValueAsString(p);
        Persona round = mapper.readValue(json, Persona.class);

        assertThat(round.getId()).isEqualTo(p.getId());
        assertThat(round.getFamily()).isEqualTo(PersonaFamily.CONVERSATIONAL);
        assertThat(round.getDisplayName()).isEqualTo(p.getDisplayName());
        assertThat(round.getSkillIds()).containsExactlyElementsOf(p.getSkillIds());
        assertThat(round.getVoicePreset().getProviderVoiceId()).isEqualTo("adam");
        assertThat(round.getVoicePreset().getStability()).isEqualTo(0.5);
        assertThat(round.isActive()).isTrue();
        assertThat(round.getVersion()).isEqualTo(1);
    }

    @Test
    void serializationRoundTrip_nullVoicePresetForPdlcPersona() throws Exception {
        Persona p = Persona.create("implementer", PersonaFamily.PDLC,
                "Implementer", "Writes code", "system",
                "claude-sonnet-4-6", List.of("read", "write", "bash"), null);
        String json = mapper.writeValueAsString(p);
        Persona round = mapper.readValue(json, Persona.class);
        assertThat(round.getVoicePreset()).isNull();
        assertThat(round.getFamily()).isEqualTo(PersonaFamily.PDLC);
    }
}
