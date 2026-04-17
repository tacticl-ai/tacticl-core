package io.tacticl.service.pipeline.controller;

import io.tacticl.business.pipeline.service.PipelineEventEmitter;
import io.tacticl.business.pipeline.dto.PipelineCallbackEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PipelineCallbackControllerTest {

    @Mock PipelineEventEmitter pipelineEventEmitter;
    @InjectMocks PipelineCallbackController controller;

    @Test
    void handleCallback_emitsEventAndReturns200() {
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            "run-1", "ROLE_COMPLETED", "PM", "PRODUCT", "{}"
        );

        ResponseEntity<Void> response = controller.handleCallback(event);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(pipelineEventEmitter).emit("run-1", "ROLE_COMPLETED", "{}");
    }

    @Test
    void handleCallback_pipelineCompleted_emitsAndCompletes() {
        PipelineCallbackEvent event = new PipelineCallbackEvent(
            "run-1", "PIPELINE_COMPLETED", null, null, "{}"
        );

        controller.handleCallback(event);

        verify(pipelineEventEmitter).emit("run-1", "PIPELINE_COMPLETED", "{}");
        verify(pipelineEventEmitter).completeAll("run-1");
    }
}
