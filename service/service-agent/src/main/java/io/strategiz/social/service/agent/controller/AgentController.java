package io.strategiz.social.service.agent.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.annotation.RequireAuth;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.tacticl.business.agent.command.AgentCommand;
import io.tacticl.business.agent.command.AgentCommandResult;
import io.tacticl.business.agent.command.AgentCommandService;
import io.tacticl.business.agent.transcription.TranscriptionService;
import io.strategiz.social.service.agent.dto.AgentCommandRequest;
import io.strategiz.social.service.agent.dto.AgentCommandResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** REST controller for the cloud agent orchestrator. */
@RestController
@RequestMapping("/v1/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentCommandService agentCommandService;

    /**
     * Optional so the application boots in profiles where
     * {@code tacticl.whisper.enabled=false} (default local). When absent,
     * {@link #executeVoice} returns 503; {@link #executeCommand} is unaffected.
     */
    private final Optional<TranscriptionService> transcriptionService;

    public AgentController(AgentCommandService agentCommandService,
                           Optional<TranscriptionService> transcriptionService) {
        this.agentCommandService = agentCommandService;
        this.transcriptionService = transcriptionService;
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

    @PostMapping(value = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireAuth
    public ResponseEntity<AgentCommandResponse> executeVoice(
            @RequestPart("audio") MultipartFile audio,
            @RequestParam(value = "model", required = false) String model,
            @AuthUser AuthenticatedUser user) {
        if (transcriptionService.isEmpty()) {
            log.warn("Voice intake from user {} rejected: transcription disabled", user.getUserId());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new AgentCommandResponse(
                    "Voice intake not configured.", List.of(), false, null));
        }
        if (audio == null || audio.isEmpty()) {
            log.warn("Voice intake from user {} rejected: empty audio", user.getUserId());
            return ResponseEntity.badRequest().body(new AgentCommandResponse(
                    "Audio file is empty.", List.of(), false, null));
        }

        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (java.io.IOException e) {
            log.warn("Voice intake from user {} failed to read upload: {}",
                    user.getUserId(), e.getMessage());
            return ResponseEntity.badRequest().body(new AgentCommandResponse(
                    "Could not read uploaded audio.", List.of(), false, null));
        }

        String filename = audio.getOriginalFilename() != null && !audio.getOriginalFilename().isBlank()
                ? audio.getOriginalFilename()
                : "audio";
        String contentType = audio.getContentType() != null ? audio.getContentType() : "application/octet-stream";

        log.info("Voice intake from user {}: {} bytes ({}, {})",
                user.getUserId(), bytes.length, filename, contentType);

        String transcript = transcriptionService.get().transcribe(bytes, filename, contentType);
        AgentCommand cmd = AgentCommand.fromHttp(user.getUserId(), transcript, model);
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
