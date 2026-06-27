package io.tacticl.client.arbiter;

import cidadel.ai.arbiter.pipeline.v1.ArbiterPipelineServiceGrpc;
import cidadel.ai.arbiter.pipeline.v1.ResolveCheckpointRequest;
import cidadel.ai.arbiter.pipeline.v1.ResolveCheckpointResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
// DTO submit types imported by simple name; proto submit types are fully-qualified in-body
// (cidadel.ai.arbiter.pipeline.v1.*) to avoid the simple-name collision with the DTOs.
import io.tacticl.client.arbiter.dto.SubmitPipelineRequest;
import io.tacticl.client.arbiter.dto.SubmitPipelineResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArbiterGrpcClientImplTest {

    @Mock ArbiterPipelineServiceGrpc.ArbiterPipelineServiceBlockingStub stub;

    ArbiterGrpcClientImpl client;

    @BeforeEach
    void setUp() {
        client = new ArbiterGrpcClientImpl(stub, "gs://tacticl/registry");
    }

    @Test
    void resolveCheckpoint_sendsCorrectProtoRequest() {
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.resolveCheckpoint(any())).thenReturn(
            ResolveCheckpointResponse.newBuilder().setAccepted(true).build()
        );

        client.resolveCheckpoint("arb-pipeline-1", "cp-111", "APPROVED", "looks good");

        ArgumentCaptor<ResolveCheckpointRequest> captor =
            ArgumentCaptor.forClass(ResolveCheckpointRequest.class);
        verify(stub).resolveCheckpoint(captor.capture());
        assertThat(captor.getValue().getPipelineId()).isEqualTo("arb-pipeline-1");
        assertThat(captor.getValue().getCheckpointId()).isEqualTo("cp-111");
        assertThat(captor.getValue().getDecision()).isEqualTo("APPROVED");
        assertThat(captor.getValue().getFeedback()).isEqualTo("looks good");
    }

    @Test
    void resolveCheckpoint_nullFeedback_sendsEmptyString() {
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.resolveCheckpoint(any())).thenReturn(
            ResolveCheckpointResponse.newBuilder().setAccepted(true).build()
        );

        client.resolveCheckpoint("arb-pipeline-1", "cp-111", "REWORK", null);

        ArgumentCaptor<ResolveCheckpointRequest> captor =
            ArgumentCaptor.forClass(ResolveCheckpointRequest.class);
        verify(stub).resolveCheckpoint(captor.capture());
        assertThat(captor.getValue().getFeedback()).isEqualTo("");
    }

    /**
     * Guard: the product discriminator (and the other wire fields the arbiter routes on) must be
     * threaded from the caller-supplied DTO onto the proto SubmitPipelineRequest — never defaulted
     * or dropped at the gRPC boundary. The idempotency key carries the pipelineRunId so the arbiter
     * derives workflowId = pdlc-{key} on the Temporal path.
     */
    @Test
    void submitPipeline_threadsProductAndContext_toProtoRequest() {
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.submitPipeline(any())).thenReturn(
            cidadel.ai.arbiter.pipeline.v1.SubmitPipelineResponse.newBuilder()
                .setPipelineId("pdlc-run-1").setStatus("PENDING").build()
        );

        SubmitPipelineRequest dto = new SubmitPipelineRequest(
            "strategiz", "run-1", "spark-1", "user-1", "BUG_FIX", "fix the thing",
            "https://github.com/acme/repo.git", "gh-token", List.of("REVIEWER"), 12.5,
            "https://api.tacticl.ai/v1/internal/pipeline/callback",
            Map.of("implementer", "id"), "{\"name\":\"BUG_FIX\"}", "tacticl-user-1",
            Map.of("implementer", 600)
        );

        SubmitPipelineResponse resp = client.submitPipeline(dto);

        ArgumentCaptor<cidadel.ai.arbiter.pipeline.v1.SubmitPipelineRequest> captor =
            ArgumentCaptor.forClass(cidadel.ai.arbiter.pipeline.v1.SubmitPipelineRequest.class);
        verify(stub).submitPipeline(captor.capture());
        cidadel.ai.arbiter.pipeline.v1.SubmitPipelineRequest proto = captor.getValue();
        // Product is the primary discriminator on the wire — threaded straight from the DTO.
        assertThat(proto.getProduct()).isEqualTo("strategiz");
        assertThat(proto.getPipelineName()).isEqualTo("BUG_FIX");
        assertThat(proto.getIdempotencyKey()).isEqualTo("run-1");
        assertThat(proto.getUserId()).isEqualTo("user-1");
        assertThat(proto.getRepoUrl()).isEqualTo("https://github.com/acme/repo.git");
        assertThat(proto.getCallbackUrl()).isEqualTo("https://api.tacticl.ai/v1/internal/pipeline/callback");
        assertThat(proto.getKnowledgeNamespace()).isEqualTo("tacticl-user-1");
        assertThat(proto.getRequestContextJson()).contains("fix the thing");
        assertThat(resp.arbiterPipelineId()).isEqualTo("pdlc-run-1");
    }

    /**
     * HARD-FAILURE GUARD: a Temporal-routing gRPC error (FAILED_PRECONDITION) must propagate out of
     * the only submit path as-is. No catch, no swallow, no fallback to a legacy / in-JVM submit, and
     * no retry onto a non-Temporal path.
     */
    @Test
    void submitPipeline_grpcFailedPrecondition_propagates() {
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
        when(stub.submitPipeline(any())).thenThrow(
            new StatusRuntimeException(
                Status.FAILED_PRECONDITION.withDescription("tenant not routable on the Temporal path"))
        );

        SubmitPipelineRequest dto = new SubmitPipelineRequest(
            "tacticl", "run-2", "spark-2", "user-2", "FULL_PDLC", "build it",
            "https://github.com/acme/repo.git", "gh-token", List.of(), 10.0,
            "https://api.tacticl.ai/v1/internal/pipeline/callback",
            Map.of(), "{}", "tacticl-user-2", Map.of()
        );

        assertThatThrownBy(() -> client.submitPipeline(dto))
            .isInstanceOf(StatusRuntimeException.class)
            .satisfies(t -> assertThat(Status.fromThrowable(t).getCode())
                .isEqualTo(Status.Code.FAILED_PRECONDITION));
    }
}
