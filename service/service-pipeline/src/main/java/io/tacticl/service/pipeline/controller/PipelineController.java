package io.tacticl.service.pipeline.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.pipeline.service.PdlcV2Service;
import io.tacticl.business.pipeline.service.PipelineEventEmitter;
import io.tacticl.data.pipeline.entity.CheckpointDecision;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.service.pipeline.dto.PipelineEventDto;
import io.tacticl.service.pipeline.dto.PipelineRunDto;
import io.tacticl.service.pipeline.dto.ResolveCheckpointDto;
import io.tacticl.service.pipeline.dto.SubmitPipelineDto;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/v1/sparks/{sparkId}/pipeline")
public class PipelineController extends BaseController {

    private final PdlcV2Service pdlcV2Service;
    private final PipelineEventEmitter pipelineEventEmitter;

    public PipelineController(PdlcV2Service pdlcV2Service,
                              PipelineEventEmitter pipelineEventEmitter) {
        this.pdlcV2Service = pdlcV2Service;
        this.pipelineEventEmitter = pipelineEventEmitter;
    }

    @Override
    protected String getModuleName() { return "pipeline"; }

    @GetMapping
    public ResponseEntity<PipelineRunDto> getPipelineStatus(
            @AuthUser AuthenticatedUser user,
            @PathVariable String sparkId) {
        return pdlcV2Service.getStatus(user.getUserId(), sparkId)
                .map(run -> ResponseEntity.ok(PipelineRunDto.from(run)))
                .orElse(ResponseEntity.<PipelineRunDto>notFound().build());
    }

    @PostMapping
    public ResponseEntity<PipelineRunDto> submitPipeline(
            @AuthUser AuthenticatedUser user,
            @PathVariable String sparkId,
            @RequestBody SubmitPipelineDto body) {
        PipelineRun run = pdlcV2Service.submitPipeline(
            user.getUserId(), sparkId,
            body.sparkRequest() != null ? body.sparkRequest() : "",
            body.repoUrl(),
            body.playbook() != null ? body.playbook() : "FULL_PDLC",
            body.skipRoles() != null ? body.skipRoles() : java.util.List.of(),
            body.githubToken(),
            body.costCeilingUsd() > 0 ? body.costCeilingUsd() : 50.0
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(PipelineRunDto.from(run));
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPipelineEvents(
            @AuthUser AuthenticatedUser user,
            @PathVariable String sparkId) {
        var run = pdlcV2Service.getStatus(user.getUserId(), sparkId)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Pipeline run not found for spark: " + sparkId));
        SseEmitter emitter = new SseEmitter(300_000L);
        return pipelineEventEmitter.register(run.getId(), emitter);
    }

    @PostMapping("/checkpoint/{checkpointId}")
    public ResponseEntity<Void> resolveCheckpoint(
            @AuthUser AuthenticatedUser user,
            @PathVariable String sparkId,
            @PathVariable String checkpointId,
            @RequestBody ResolveCheckpointDto body) {
        pdlcV2Service.resolveCheckpoint(
            user.getUserId(), sparkId, checkpointId,
            parseDecision(body.decision()),
            body.feedback()
        );
        return ResponseEntity.ok().build();
    }

    @GetMapping("/events/history")
    public ResponseEntity<Page<PipelineEventDto>> getEventHistory(
            @AuthUser AuthenticatedUser user,
            @PathVariable String sparkId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PipelineRun run = pdlcV2Service.getStatus(user.getUserId(), sparkId)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Pipeline run not found for spark: " + sparkId));
        return ResponseEntity.ok(pdlcV2Service.getEvents(run.getId(), page, size)
                .map(PipelineEventDto::from));
    }

    @DeleteMapping
    public ResponseEntity<Void> cancelPipeline(
            @AuthUser AuthenticatedUser user,
            @PathVariable String sparkId) {
        pdlcV2Service.cancelPipeline(user.getUserId(), sparkId);
        return ResponseEntity.ok().build();
    }

    private CheckpointDecision parseDecision(String decision) {
        try {
            return CheckpointDecision.valueOf(decision);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid checkpoint decision: " + decision + ". Valid values: APPROVED, REWORK, CANCEL");
        }
    }
}
