package io.tacticl.client.arbiter;

import cidadel.ai.arbiter.pipeline.v1.ArbiterPipelineServiceGrpc;
import cidadel.ai.arbiter.pipeline.v1.ResolveCheckpointRequest;
import cidadel.ai.arbiter.pipeline.v1.ResolveCheckpointResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
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
}
