package io.tacticl.service.pipeline.controller;

import io.tacticl.business.pipeline.service.PdlcV2Service;
import io.tacticl.service.pipeline.dto.ArbiterCallbackDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineCallbackControllerTest {

    @Test
    void givenCompletedCallback_thenCallsHandleArbiterCallbackWithCorrectArgs() {
        PdlcV2Service service = mock(PdlcV2Service.class);
        var controller = new PipelineCallbackController(service, "");

        ArbiterCallbackDto body = new ArbiterCallbackDto(
            "arb-uuid-1", null, null, null, "COMPLETED", "{}", null, 1234L);

        ResponseEntity<Void> resp = controller.handleCallback(null, body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).handleArbiterCallback(
            "arb-uuid-1", null, null, null, "COMPLETED", null);
    }

    @Test
    void givenAgentCompletedCallback_thenCallsHandleArbiterCallback() {
        PdlcV2Service service = mock(PdlcV2Service.class);
        var controller = new PipelineCallbackController(service, "");

        ArbiterCallbackDto body = new ArbiterCallbackDto(
            "arb-uuid-2", "agent_completed", "pm-abc12345-ff01", "done",
            null, null, null, null);

        ResponseEntity<Void> resp = controller.handleCallback(null, body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).handleArbiterCallback(
            "arb-uuid-2", "agent_completed", "pm-abc12345-ff01", "done", null, null);
    }

    @Test
    void givenWrongSecret_thenReturns401() {
        PdlcV2Service service = mock(PdlcV2Service.class);
        var controller = new PipelineCallbackController(service, "super-secret");

        ArbiterCallbackDto body = new ArbiterCallbackDto(
            "arb-uuid-3", "progress", "researcher-abcd1234-ee02", null,
            null, null, null, null);

        ResponseEntity<Void> resp = controller.handleCallback("wrong", body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(service);
    }

    @Test
    void givenBlankSecret_thenAllowsRequest() {
        PdlcV2Service service = mock(PdlcV2Service.class);
        var controller = new PipelineCallbackController(service, "");

        ArbiterCallbackDto body = new ArbiterCallbackDto(
            "arb-uuid-4", "progress", "pm-abcd1234-aa01", null,
            null, null, null, null);

        ResponseEntity<Void> resp = controller.handleCallback(null, body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).handleArbiterCallback(
            "arb-uuid-4", "progress", "pm-abcd1234-aa01", null, null, null);
    }
}
