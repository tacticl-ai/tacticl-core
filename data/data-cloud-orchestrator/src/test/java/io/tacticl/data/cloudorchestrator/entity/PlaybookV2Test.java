package io.tacticl.data.cloudorchestrator.entity;

import io.tacticl.data.pipeline.entity.PdlcPhase;
import io.tacticl.data.sparks.entity.SparkType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PlaybookV2Test {

    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void create_setsDefaultsAndPhases() {
        PhaseConfig phase = new PhaseConfig(PdlcPhase.PRODUCT,
                List.of(new RoleSlot("product-owner", false, 3)),
                false, true);
        PlaybookV2 pb = PlaybookV2.create("FULL_PDLC", "Full PDLC",
                "All phases", List.of(SparkType.CODE), List.of(phase));

        assertThat(pb.getId()).isEqualTo("FULL_PDLC");
        assertThat(pb.isActive()).isTrue();
        assertThat(pb.getVersion()).isEqualTo(1);
        assertThat(pb.getSparkTypes()).containsExactly(SparkType.CODE);
        assertThat(pb.getPhases()).hasSize(1);
        assertThat(pb.getPhases().get(0).getPhase()).isEqualTo(PdlcPhase.PRODUCT);
    }

    @Test
    void serializationRoundTrip_preservesNestedPhasesAndRoles() throws Exception {
        PhaseConfig phase1 = new PhaseConfig(PdlcPhase.PRODUCT,
                List.of(new RoleSlot("product-owner", false, 3),
                        new RoleSlot("researcher", true, 2)),
                true, true);
        PhaseConfig phase2 = new PhaseConfig(PdlcPhase.DEVELOPMENT,
                List.of(new RoleSlot("implementer", false, 5)),
                false, false);
        PlaybookV2 pb = PlaybookV2.create("BUG_FIX", "Bug Fix Playbook",
                "Quick targeted fix",
                List.of(SparkType.CODE, SparkType.DEVOPS),
                List.of(phase1, phase2));

        String json = mapper.writeValueAsString(pb);
        PlaybookV2 round = mapper.readValue(json, PlaybookV2.class);

        assertThat(round.getId()).isEqualTo("BUG_FIX");
        assertThat(round.getSparkTypes()).containsExactly(SparkType.CODE, SparkType.DEVOPS);
        assertThat(round.getPhases()).hasSize(2);
        assertThat(round.getPhases().get(0).getRoles().get(1).getPersonaId()).isEqualTo("researcher");
        assertThat(round.getPhases().get(0).getRoles().get(1).isOptional()).isTrue();
        assertThat(round.getPhases().get(0).isParallel()).isTrue();
        assertThat(round.getPhases().get(1).getPhase()).isEqualTo(PdlcPhase.DEVELOPMENT);
    }
}
