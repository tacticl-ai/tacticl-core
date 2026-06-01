package io.tacticl.data.cloudorchestrator.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TurnTest {

    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void user_factorySetsRoleAndModality() {
        Turn t = Turn.user("hello", "voice");
        assertThat(t.getRole()).isEqualTo("user");
        assertThat(t.getModality()).isEqualTo("voice");
        assertThat(t.getPersonaId()).isNull();
        assertThat(t.getId()).isNotBlank();
    }

    @Test
    void assistant_factorySetsPersonaId() {
        Turn t = Turn.assistant("product-manager", "how can I help?", "text");
        assertThat(t.getRole()).isEqualTo("assistant");
        assertThat(t.getPersonaId()).isEqualTo("product-manager");
    }

    @Test
    void serializationRoundTrip_withToolCallsAndLatency() throws Exception {
        JsonNode input = mapper.readTree("{\"query\":\"foo\"}");
        JsonNode output = mapper.readTree("{\"results\":[]}");

        Turn t = Turn.assistant("market-researcher", "let me search", "voice");
        t.setToolCalls(List.of(new ToolCall("web_search", input, output, 120L, null)));
        t.setLatencyMs(new LatencyBreakdown(220, 5, 350, 180, 980));
        t.setTokens(new TokenUsage(100, 80, "claude-sonnet-4-6"));
        t.setPartialTranscripts(List.of(
                new PartialTranscript("let", 0.9, false, Instant.now()),
                new PartialTranscript("let me search", 0.95, true, Instant.now())));

        String json = mapper.writeValueAsString(t);
        Turn round = mapper.readValue(json, Turn.class);

        assertThat(round.getToolCalls()).hasSize(1);
        assertThat(round.getToolCalls().get(0).getToolName()).isEqualTo("web_search");
        assertThat(round.getToolCalls().get(0).getInput().get("query").asString()).isEqualTo("foo");
        assertThat(round.getLatencyMs().getTotalMs()).isEqualTo(980);
        assertThat(round.getTokens().getModel()).isEqualTo("claude-sonnet-4-6");
        assertThat(round.getPartialTranscripts()).hasSize(2);
        assertThat(round.getPartialTranscripts().get(1).isFinal()).isTrue();
    }
}
