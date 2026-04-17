package io.tacticl.data.pipeline.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PipelineEventTest {

    @Test
    void create_setsAllFields() {
        PipelineEvent event = PipelineEvent.create("run-1", "ROLE_COMPLETED",
                                                    "PM", "PRODUCT", "{\"cost\":2.1}");
        assertThat(event.getId()).isNotNull();
        assertThat(event.getPipelineRunId()).isEqualTo("run-1");
        assertThat(event.getEventType()).isEqualTo("ROLE_COMPLETED");
        assertThat(event.getRole()).isEqualTo("PM");
        assertThat(event.getPhase()).isEqualTo("PRODUCT");
        assertThat(event.getPayloadJson()).isEqualTo("{\"cost\":2.1}");
        assertThat(event.getTimestamp()).isNotNull();
    }
}
