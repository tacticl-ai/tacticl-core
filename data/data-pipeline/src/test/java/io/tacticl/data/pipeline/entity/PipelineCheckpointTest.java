package io.tacticl.data.pipeline.entity;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class PipelineCheckpointTest {

    @Test
    void create_isPendingWithNoDecision() {
        PipelineCheckpoint cp = PipelineCheckpoint.create("run-1", "spark-1", "PRODUCT",
                                                           "PHASE_COMPLETE", Map.of());
        assertThat(cp.getId()).isNotNull();
        assertThat(cp.getStatus()).isEqualTo("PENDING");
        assertThat(cp.getDecision()).isNull();
        assertThat(cp.getResolvedAt()).isNull();
    }

    @Test
    void resolve_approved_setsDecisionAndResolvedAt() {
        PipelineCheckpoint cp = PipelineCheckpoint.create("run-1", "spark-1", "PRODUCT",
                                                           "PHASE_COMPLETE", Map.of());
        cp.resolve(CheckpointDecision.APPROVED, null);
        assertThat(cp.getStatus()).isEqualTo("RESOLVED");
        assertThat(cp.getDecision()).isEqualTo("APPROVED");
        assertThat(cp.getResolvedAt()).isNotNull();
    }

    @Test
    void resolve_rework_storesFeedback() {
        PipelineCheckpoint cp = PipelineCheckpoint.create("run-1", "spark-1", "PRODUCT",
                                                           "PHASE_COMPLETE", Map.of());
        cp.resolve(CheckpointDecision.REWORK, "Please add acceptance criteria");
        assertThat(cp.getDecision()).isEqualTo("REWORK");
        assertThat(cp.getFeedback()).isEqualTo("Please add acceptance criteria");
    }
}
