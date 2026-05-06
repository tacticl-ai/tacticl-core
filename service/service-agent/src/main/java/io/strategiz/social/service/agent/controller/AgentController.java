package io.strategiz.social.service.agent.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.tacticl.business.agent.command.AgentCommand;
import io.tacticl.business.agent.command.AgentCommandResult;
import io.tacticl.business.agent.command.AgentCommandService;
import io.strategiz.social.service.agent.dto.AgentCommandRequest;
import io.strategiz.social.service.agent.dto.AgentCommandResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for the cloud agent orchestrator. */
@RestController
@RequestMapping("/v1/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentCommandService agentCommandService;

    public AgentController(AgentCommandService agentCommandService) {
        this.agentCommandService = agentCommandService;
    }

    @PostMapping("/command")
    @RequireAuth
    public ResponseEntity<AgentCommandResponse> executeCommand(
            @Valid @RequestBody AgentCommandRequest request,
            @AuthUser AuthenticatedUser user) {
        String text = request.getText();
        log.info("Agent command from user {}: {}", user.getUserId(),
                text.length() > 100 ? text.substring(0, 100) + "..." : text);

        AgentCommand cmd = AgentCommand.fromHttp(user.getUserId(), text, request.getModel());
        AgentCommandResult result = agentCommandService.execute(cmd);
        return ResponseEntity.ok(toResponse(result));
    }

    private AgentCommandResponse toResponse(AgentCommandResult result) {
        if (result.pipelineRunId() != null) {
            return AgentCommandResponse.pipeline(result.sparkId(), result.pipelineRunId(),
                    result.pipelineTier(), result.pipelineTier(), List.of());
        }
        AgentCommandResponse resp = new AgentCommandResponse(
                result.responseText(), List.of(), result.succeeded(), result.model());
        resp.setSparkId(result.sparkId());
        resp.setSparkStatus(result.sparkStatus());
        resp.setExecutionMode(result.executionMode());
        resp.setPipelineTier(result.pipelineTier());
        return resp;
    }

    @GetMapping("/history")
    @RequireAuth
    public ResponseEntity<List<?>> getHistory(@AuthUser AuthenticatedUser user) {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/models")
    @RequireAuth
    public ResponseEntity<List<String>> getAvailableModels() {
        return ResponseEntity.ok(List.of(
                "claude-sonnet-4-6",
                "claude-opus-4-6",
                "claude-haiku-4-5-20251001"
        ));
    }
}
