package io.tacticl.data.pipeline.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the PM→PO rename per SAD §4.3.
 *
 * <p>The migration runner bulk-updates {@code pipeline_runs.role} and
 * {@code pipeline_events.role} Mongo records from {@code "PM"} to {@code "PO"} in-place,
 * so no {@code @JsonAlias} is needed — the old string must NOT be a valid enum value.
 */
class PdlcRoleTest {

    @Test
    void po_resolvesAndIsTheFirstValue() {
        assertThat(PdlcRole.PO).isEqualTo(PdlcRole.values()[0]);
        assertThat(PdlcRole.valueOf("PO")).isEqualTo(PdlcRole.PO);
    }

    @Test
    void pm_isNotARecognizedEnumValue() {
        assertThatThrownBy(() -> PdlcRole.valueOf("PM"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enumHasTwelveValues() {
        assertThat(PdlcRole.values()).hasSize(12);
    }
}
