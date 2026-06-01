package io.tacticl.data.cloudorchestrator.entity;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CostBreakdownTest {

    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void addProviderCosts_updatesTotals() {
        CostBreakdown cb = new CostBreakdown();
        cb.addLlm(0.10);
        cb.addStt(0.02);
        cb.addTts(0.05);
        assertThat(cb.totalUsd()).isEqualTo(0.17, Offset.offset(0.0001));
    }

    @Test
    void serializationRoundTrip() throws Exception {
        CostBreakdown cb = new CostBreakdown(1.23, 0.45, 0.67);
        String json = mapper.writeValueAsString(cb);
        CostBreakdown round = mapper.readValue(json, CostBreakdown.class);
        assertThat(round.getLlmUsd()).isEqualTo(1.23);
        assertThat(round.getSttUsd()).isEqualTo(0.45);
        assertThat(round.getTtsUsd()).isEqualTo(0.67);
    }
}
