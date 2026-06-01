package io.tacticl.service.pipeline.controller;

import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.tacticl.business.pipeline.service.PdlcV2Service;
import io.tacticl.business.pipeline.service.PipelineEventEmitter;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.data.pipeline.entity.PipelineStatus;
import io.tacticl.service.pipeline.dto.PipelineEventDto;
import io.tacticl.service.pipeline.dto.PipelineRunDto;
import io.tacticl.service.pipeline.dto.ResolveCheckpointDto;
import io.tacticl.service.pipeline.dto.SubmitPipelineDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    void cancelPipeline_callsServiceAndReturns200() {
        doNothing().when(pdlcV2Service).cancelPipeline(anyString(), eq("spark-1"));

        ResponseEntity<Void> response = controller.cancelPipeline(user("user-1"), "spark-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(pdlcV2Service).cancelPipeline("user-1", "spark-1");
    }

    @Test
    void getEventHistory_found_returns200WithPage() {
        PipelineRun run = PipelineRun.create("user-1", "spark-1", "req", "url", "BUG_FIX", List.of(), 10.0);
        when(pdlcV2Service.getStatus("user-1", "spark-1")).thenReturn(Optional.of(run));
        when(pdlcV2Service.getEvents(run.getId(), 0, 50)).thenReturn(Page.empty());

        ResponseEntity<Page<PipelineEventDto>> response =
                controller.getEventHistory(user("user-1"), "spark-1", 0, 50);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(pdlcV2Service).getEvents(run.getId(), 0, 50);
    }

    @Test
    void givenValidSubmit_thenReturns201WithPipelineRunDto() {
        PipelineRun run = PipelineRun.create("user-1", "spark-1", "req", "https://github.com/foo/bar",
                "FULL_PDLC", List.of(), 50.0);
        when(pdlcV2Service.submitPipeline(
                eq("tacticl"), eq("user-1"), eq("spark-1"), eq("req"), eq("https://github.com/foo/bar"),
                eq("FULL_PDLC"), eq(List.of()), eq("gh-token"), anyDouble()))
                .thenReturn(run);

        SubmitPipelineDto body = new SubmitPipelineDto(
                "req", "https://github.com/foo/bar", "FULL_PDLC",
                List.of(), "gh-token", 0.0);

        ResponseEntity<PipelineRunDto> response =
                controller.submitPipeline(user("user-1"), "spark-1", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        verify(pdlcV2Service).submitPipeline(
                eq("tacticl"), eq("user-1"), eq("spark-1"), eq("req"), eq("https://github.com/foo/bar"),
                eq("FULL_PDLC"), eq(List.of()), eq("gh-token"), eq(50.0));
    }

    @Test
    void getEventHistory_notFound_throws404() {
        when(pdlcV2Service.getStatus("user-1", "spark-1")).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> controller.getEventHistory(user("user-1"), "spark-1", 0, 50)
        );
    }
}
