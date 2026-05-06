package io.strategiz.social.service.agent.controller;

import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.strategiz.social.service.agent.dto.AgentCommandRequest;
import io.strategiz.social.service.agent.dto.AgentCommandResponse;
import io.tacticl.business.agent.command.AgentCommand;
import io.tacticl.business.agent.command.AgentCommandResult;
import io.tacticl.business.agent.command.AgentCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentControllerTest {

    private AgentCommandService agentCommandService;
    private AgentController controller;

    @BeforeEach
    void setUp() {
        agentCommandService = mock(AgentCommandService.class);
        controller = new AgentController(agentCommandService);
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
}
