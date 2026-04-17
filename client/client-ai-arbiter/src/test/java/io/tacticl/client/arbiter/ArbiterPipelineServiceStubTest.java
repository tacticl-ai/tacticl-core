package io.tacticl.client.arbiter;

import io.tacticl.client.arbiter.dto.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ArbiterPipelineServiceStubTest {

    private final ArbiterPipelineServiceStub stub = new ArbiterPipelineServiceStub();

    @Test
    void submitPipeline_returnsRunIdWithPendingStatus() {
        SubmitPipelineRequest request = new SubmitPipelineRequest(
            "run-1", "spark-1", "user-1", "FULL_PDLC",
            "Add auth flow", "github.com/user/repo", "gh-token",
            List.of(), 50.0, "https://api.tacticl.ai/v1/internal/pipeline/callback"
        );
        SubmitPipelineResponse response = stub.submitPipeline(request);
        assertThat(response.pipelineRunId()).isEqualTo("run-1");
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void resolveCheckpoint_doesNotThrow() {
        ResolveCheckpointRequest request = new ResolveCheckpointRequest(
            "run-1", "cp-1", "APPROVED", null
        );
        assertThatCode(() -> stub.resolveCheckpoint(request)).doesNotThrowAnyException();
    }

    @Test
    void getPipelineStatus_returnsUnknown() {
        PipelineStatusResponse response = stub.getPipelineStatus("run-1");
        assertThat(response.pipelineRunId()).isEqualTo("run-1");
        assertThat(response.status()).isEqualTo("UNKNOWN");
    }

    @Test
    void cancelPipeline_doesNotThrow() {
        assertThatCode(() -> stub.cancelPipeline("run-1")).doesNotThrowAnyException();
    }
}
