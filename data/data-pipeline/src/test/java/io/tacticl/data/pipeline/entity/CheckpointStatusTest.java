package io.tacticl.data.pipeline.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckpointStatusTest {

    @Test
    void containsAllSpecStates() {
        assertThat(CheckpointStatus.values())
                .contains(CheckpointStatus.OPEN, CheckpointStatus.RESOLVED, CheckpointStatus.CANCELLED);
    }

    @Test
    void legacyPendingValueIsStillResolvable() {
        // Kept for backward compat with pre-v2 Mongo records.
        assertThat(CheckpointStatus.valueOf("PENDING")).isEqualTo(CheckpointStatus.PENDING);
    }
}
