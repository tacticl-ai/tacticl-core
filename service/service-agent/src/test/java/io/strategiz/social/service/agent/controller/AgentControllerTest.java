package io.strategiz.social.service.agent.controller;

import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.service.agent.dto.AgentCommandRequest;
import io.strategiz.social.service.agent.dto.AgentCommandResponse;
import io.tacticl.business.agent.command.AgentCommand;
import io.tacticl.business.agent.command.AgentCommandResult;
import io.tacticl.business.agent.command.AgentCommandService;
import io.tacticl.business.agent.transcription.TranscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentControllerTest {

    private AgentCommandService agentCommandService;
    private TranscriptionService transcriptionService;
    private AgentController controller;

    @BeforeEach
    void setUp() {
        agentCommandService = mock(AgentCommandService.class);
        transcriptionService = mock(TranscriptionService.class);
        controller = new AgentController(agentCommandService, transcriptionService);
    }

    @Test
    void delegatesToAgentCommandService() {
        AuthenticatedUser user = mock(AuthenticatedUser.class);
        when(user.getUserId()).thenReturn("u1");

        AgentCommandRequest req = new AgentCommandRequest();
        req.setText("hi");
        req.setModel("claude-haiku-4-5");

        when(agentCommandService.execute(any(AgentCommand.class)))
                .thenReturn(AgentCommandResult.cloudCompleted("s1", "ok", "claude-haiku-4-5", 7));

        ResponseEntity<AgentCommandResponse> resp = controller.executeCommand(req, user);

        assertThat(resp.getBody().getResponseText()).isEqualTo("ok");
        assertThat(resp.getBody().getSparkId()).isEqualTo("s1");

        ArgumentCaptor<AgentCommand> captor = ArgumentCaptor.forClass(AgentCommand.class);
        verify(agentCommandService).execute(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo("u1");
        assertThat(captor.getValue().text()).isEqualTo("hi");
        assertThat(captor.getValue().model()).isEqualTo("claude-haiku-4-5");
    }

    @Test
    void voiceEndpointTranscribesThenExecutes() {
        MockMultipartFile file = new MockMultipartFile(
                "audio", "v.ogg", "audio/ogg", "bytes".getBytes());
        AuthenticatedUser user = mock(AuthenticatedUser.class);
        when(user.getUserId()).thenReturn("u1");

        when(transcriptionService.transcribe(any(byte[].class), eq("v.ogg"), eq("audio/ogg")))
                .thenReturn("hello world");
        when(agentCommandService.execute(any(AgentCommand.class)))
                .thenReturn(AgentCommandResult.cloudCompleted("s1", "ok", "claude-sonnet-4-6", 7));

        ResponseEntity<AgentCommandResponse> resp = controller.executeVoice(file, null, user);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody().getResponseText()).isEqualTo("ok");
        assertThat(resp.getBody().getSparkId()).isEqualTo("s1");

        ArgumentCaptor<AgentCommand> cap = ArgumentCaptor.forClass(AgentCommand.class);
        verify(agentCommandService).execute(cap.capture());
        assertThat(cap.getValue().userId()).isEqualTo("u1");
        assertThat(cap.getValue().text()).isEqualTo("hello world");
        assertThat(cap.getValue().model()).isNull();
    }

    @Test
    void voiceEndpointPassesThroughExplicitModel() {
        MockMultipartFile file = new MockMultipartFile(
                "audio", "v.ogg", "audio/ogg", "bytes".getBytes());
        AuthenticatedUser user = mock(AuthenticatedUser.class);
        when(user.getUserId()).thenReturn("u1");

        when(transcriptionService.transcribe(any(byte[].class), eq("v.ogg"), eq("audio/ogg")))
                .thenReturn("transcribed text");
        when(agentCommandService.execute(any(AgentCommand.class)))
                .thenReturn(AgentCommandResult.cloudCompleted("s2", "done", "claude-haiku-4-5", 3));

        ResponseEntity<AgentCommandResponse> resp = controller.executeVoice(file, "claude-haiku-4-5", user);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();

        ArgumentCaptor<AgentCommand> cap = ArgumentCaptor.forClass(AgentCommand.class);
        verify(agentCommandService).execute(cap.capture());
        assertThat(cap.getValue().model()).isEqualTo("claude-haiku-4-5");
        assertThat(cap.getValue().text()).isEqualTo("transcribed text");
    }

    @Test
    void voiceEndpointReturns400ForEmptyAudio() {
        MockMultipartFile empty = new MockMultipartFile(
                "audio", "v.ogg", "audio/ogg", new byte[0]);
        AuthenticatedUser user = mock(AuthenticatedUser.class);

        ResponseEntity<AgentCommandResponse> resp = controller.executeVoice(empty, null, user);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getResponseText()).contains("empty");
        verify(transcriptionService, never()).transcribe(any(), any(), any());
        verify(agentCommandService, never()).execute(any());
    }
}
