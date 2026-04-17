package io.tacticl.service.pipeline.controller;

import io.tacticl.business.pipeline.dto.PipelineCallbackEvent;
import io.tacticl.business.pipeline.service.PdlcV2Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineCallbackControllerTest {

    private static final PipelineCallbackEvent EVENT =
        new PipelineCallbackEvent("run-1", "ROLE_COMPLETED", "PM", "PRODUCT", "{}");

    @Test
    void handleCallback_noSecretConfigured_delegates() {
        PdlcV2Service service = mock(PdlcV2Service.class);
        var controller = new PipelineCallbackController(service, "");

        ResponseEntity<Void> resp = controller.handleCallback(null, EVENT);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).handleCallbackEvent(EVENT);
    }

    @Test
    void handleCallback_correctSecret_delegates() {
        PdlcV2Service service = mock(PdlcV2Service.class);
        var controller = new PipelineCallbackController(service, "super-secret");

        ResponseEntity<Void> resp = controller.handleCallback("super-secret", EVENT);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).handleCallbackEvent(EVENT);
    }

    @Test
    void handleCallback_wrongSecret_returns401() {
        PdlcV2Service service = mock(PdlcV2Service.class);
        var controller = new PipelineCallbackController(service, "super-secret");

        ResponseEntity<Void> resp = controller.handleCallback("wrong", EVENT);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(service);
    }

    @Test
    void handleCallback_secretConfigured_missingHeader_returns401() {
        PdlcV2Service service = mock(PdlcV2Service.class);
        var controller = new PipelineCallbackController(service, "super-secret");

        ResponseEntity<Void> resp = controller.handleCallback(null, EVENT);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(service);
    }
}
