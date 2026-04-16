package io.tacticl.data.sparks.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CheckpointTest {

    @Test
    void create_setsInitialState() {
        Checkpoint cp = Checkpoint.create("spark-1", "user-1",
                CheckpointType.APPROVAL, "Approve PR to main?");
        assertThat(cp.getId()).isNotBlank();
        assertThat(cp.getSparkId()).isEqualTo("spark-1");
        assertThat(cp.getUserId()).isEqualTo("user-1");
        assertThat(cp.getType()).isEqualTo(CheckpointType.APPROVAL);
        assertThat(cp.getPrompt()).isEqualTo("Approve PR to main?");
        assertThat(cp.getStatus()).isEqualTo(CheckpointStatus.PENDING);
        assertThat(cp.getCreatedAt()).isNotNull();
    }

    @Test
    void resolve_approve_setsStatusAndTimestamp() {
        Checkpoint cp = Checkpoint.create("spark-1", "user-1",
                CheckpointType.APPROVAL, "Approve?");
        cp.resolve(CheckpointStatus.APPROVED, "looks good");
        assertThat(cp.getStatus()).isEqualTo(CheckpointStatus.APPROVED);
        assertThat(cp.getResolutionInstructions()).isEqualTo("looks good");
        assertThat(cp.getResolvedAt()).isNotNull();
    }

    @Test
    void resolve_deny_setsStatusDenied() {
        Checkpoint cp = Checkpoint.create("spark-1", "user-1",
                CheckpointType.APPROVAL, "Approve?");
        cp.resolve(CheckpointStatus.DENIED, null);
        assertThat(cp.getStatus()).isEqualTo(CheckpointStatus.DENIED);
    }
}
