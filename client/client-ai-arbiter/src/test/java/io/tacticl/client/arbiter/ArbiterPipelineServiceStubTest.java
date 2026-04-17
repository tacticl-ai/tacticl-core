package io.tacticl.client.arbiter;

import io.tacticl.client.arbiter.dto.PipelineResultResponse;
import io.tacticl.client.arbiter.dto.SubmitPipelineRequest;
import io.tacticl.client.arbiter.dto.SubmitPipelineResponse;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ArbiterPipelineServiceStubTest {

    private final ArbiterPipelineServiceStub stub = new ArbiterPipelineServiceStub();

    @Test
    void submitPipeline_returnsRunIdWithNullArbiterIdAndPendingStatus() {
        SubmitPipelineRequest request = new SubmitPipelineRequest(
            "run-1", "spark-1", "user-1", "FULL_PDLC",
            "Add auth flow", "github.com/user/repo", "gh-token",
            List.of(), 50.0, "https://api.tacticl.ai/v1/internal/pipeline/callback"
        );
        SubmitPipelineResponse response = stub.submitPipeline(request);
        assertThat(response.pipelineRunId()).isEqualTo("run-1");
        assertThat(response.arbiterPipelineId()).isNull();
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void cancelPipeline_doesNotThrow() {
        assertThatCode(() -> stub.cancelPipeline("arbiter-123")).doesNotThrowAnyException();
    }

    @Test
    void getResult_returnsUnknownStatus() {
        PipelineResultResponse response = stub.getResult("arbiter-123");
        assertThat(response.pipelineId()).isEqualTo("arbiter-123");
        assertThat(response.status()).isEqualTo("UNKNOWN");
        assertThat(response.resultJson()).isNull();
        assertThat(response.errorMessage()).isNull();
    }
}
