package io.tacticl.service.pipeline.controller;

import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.tacticl.business.pipeline.service.PdlcV2Service;
import io.tacticl.business.pipeline.service.PipelineEventEmitter;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.entity.PipelineStatus;
import io.tacticl.service.pipeline.dto.ResolveCheckpointDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineControllerTest {

    @Mock PdlcV2Service pdlcV2Service;
    @Mock PipelineEventEmitter pipelineEventEmitter;
    @InjectMocks PipelineController controller;

    private AuthenticatedUser user(String id) {
        AuthenticatedUser u = mock(AuthenticatedUser.class);
        when(u.getUserId()).thenReturn(id);
        return u;
    }

    @Test
    void getPipelineStatus_found_returns200() {
        PipelineRun run = PipelineRun.create("user-1", "spark-1", "req", "url", "BUG_FIX", List.of(), 10.0);
        when(pdlcV2Service.getStatus("user-1", "spark-1")).thenReturn(Optional.of(run));

        ResponseEntity<?> response = controller.getPipelineStatus(user("user-1"), "spark-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getPipelineStatus_notFound_returns404() {
        when(pdlcV2Service.getStatus("user-1", "spark-1")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getPipelineStatus(user("user-1"), "spark-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void resolveCheckpoint_callsServiceAndReturns200() {
        doNothing().when(pdlcV2Service).resolveCheckpoint(any(), any(), any(), any(), any());

        ResponseEntity<Void> response = controller.resolveCheckpoint(
            user("user-1"), "spark-1", "cp-1",
            new ResolveCheckpointDto("APPROVED", null)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(pdlcV2Service).resolveCheckpoint(any(), any(), any(), any(), any());
    }
}
