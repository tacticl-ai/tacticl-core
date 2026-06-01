package io.tacticl.data.cloudorchestrator.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SkillTest {

    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void create_setsDefaults() throws Exception {
        JsonNode schema = mapper.readTree("{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}");
        Skill s = Skill.create("web_search", "Web Search",
                "Search the web with Brave", schema, "BraveSearchActivity");

        assertThat(s.getId()).isEqualTo("web_search");
        assertThat(s.isActive()).isTrue();
        assertThat(s.getInputSchema().get("type").asString()).isEqualTo("object");
        assertThat(s.getActivityName()).isEqualTo("BraveSearchActivity");
    }

    @Test
    void serializationRoundTrip_preservesJsonSchema() throws Exception {
        JsonNode schema = mapper.readTree("{\"type\":\"object\",\"required\":[\"q\"]}");
        Skill s = Skill.create("propose_implementation", "Propose",
                "Persists a structured proposal", schema, "RecordProposalActivity");

        String json = mapper.writeValueAsString(s);
        Skill round = mapper.readValue(json, Skill.class);

        assertThat(round.getId()).isEqualTo(s.getId());
        assertThat(round.getInputSchema().get("type").asString()).isEqualTo("object");
        assertThat(round.getInputSchema().get("required").get(0).asString()).isEqualTo("q");
        assertThat(round.isActive()).isTrue();
        assertThat(round.getActivityName()).isEqualTo("RecordProposalActivity");
    }
}
