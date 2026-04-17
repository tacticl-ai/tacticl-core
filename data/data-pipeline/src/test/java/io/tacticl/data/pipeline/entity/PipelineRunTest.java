package io.tacticl.data.pipeline.entity;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PipelineRunTest {

    @Test
    void create_setsRequiredFieldsAndStatus() {
        PipelineRun run = PipelineRun.create("user-1", "spark-1", "Add auth flow",
                                              "github.com/user/repo", "FULL_PDLC",
                                              List.of(), 50.0);
        assertThat(run.getId()).isNotNull();
        assertThat(run.getUserId()).isEqualTo("user-1");
        assertThat(run.getSparkId()).isEqualTo("spark-1");
        assertThat(run.getStatus()).isEqualTo(PipelineStatus.PENDING);
        assertThat(run.getPlaybook()).isEqualTo("FULL_PDLC");
        assertThat(run.getCreatedAt()).isNotNull();
        assertThat(run.getTotalCostUsd()).isEqualTo(0.0);
    }

    @Test
    void markRunning_changesStatusToRunning() {
        PipelineRun run = PipelineRun.create("u", "s", "req", "url", "BUG_FIX", List.of(), 10.0);
        run.markRunning();
        assertThat(run.getStatus()).isEqualTo(PipelineStatus.RUNNING);
    }

    @Test
    void markCompleted_changesStatusAndSetsCompletedAt() {
        PipelineRun run = PipelineRun.create("u", "s", "req", "url", "BUG_FIX", List.of(), 10.0);
        run.markRunning();
        run.markCompleted();
        assertThat(run.getStatus()).isEqualTo(PipelineStatus.COMPLETED);
        assertThat(run.getCompletedAt()).isNotNull();
    }

    @Test
    void markFailed_changesStatusToFailed() {
        PipelineRun run = PipelineRun.create("u", "s", "req", "url", "BUG_FIX", List.of(), 10.0);
        run.markFailed("Arbiter unreachable");
        assertThat(run.getStatus()).isEqualTo(PipelineStatus.FAILED);
        assertThat(run.getFailureReason()).isEqualTo("Arbiter unreachable");
    }

    @Test
    void pauseAtCheckpoint_changesStatusAndStoresCheckpointId() {
        PipelineRun run = PipelineRun.create("u", "s", "req", "url", "FULL_PDLC", List.of(), 50.0);
        run.markRunning();
        run.pauseAtCheckpoint("cp-001", "PRODUCT");
        assertThat(run.getStatus()).isEqualTo(PipelineStatus.PAUSED_AT_CHECKPOINT);
        assertThat(run.getCurrentCheckpointId()).isEqualTo("cp-001");
    }

    @Test
    void addCost_accumulatesTotalCost() {
        PipelineRun run = PipelineRun.create("u", "s", "req", "url", "FULL_PDLC", List.of(), 50.0);
        run.addCost(5.25);
        run.addCost(3.10);
        assertThat(run.getTotalCostUsd()).isEqualTo(8.35, org.assertj.core.data.Offset.offset(0.001));
    }
}
