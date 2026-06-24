package io.tacticl.service.pipeline.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.pipeline.service.ArtifactRetrievalService;
import io.tacticl.business.pipeline.service.PdlcV2Service;
import io.tacticl.business.pipeline.service.PipelineEventEmitter;
import io.tacticl.data.pipeline.entity.CheckpointDecision;
import io.tacticl.data.pipeline.entity.PipelineRun;
import io.tacticl.service.pipeline.dto.ArtifactContentDto;
import io.tacticl.service.pipeline.dto.ArtifactManifestEntryDto;
import io.tacticl.service.pipeline.dto.PipelineEventDto;
import io.tacticl.service.pipeline.dto.PipelineRunDto;
import io.tacticl.service.pipeline.dto.ResolveCheckpointDto;
import io.tacticl.service.pipeline.dto.RoleArtifactDto;
import io.tacticl.service.pipeline.dto.SubmitPipelineDto;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final ArtifactRetrievalService artifactRetrievalService;

    public PipelineController(PdlcV2Service pdlcV2Service,
                              PipelineEventEmitter pipelineEventEmitter,
                              ArtifactRetrievalService artifactRetrievalService) {
        this.pdlcV2Service = pdlcV2Service;
        this.pipelineEventEmitter = pipelineEventEmitter;
        this.artifactRetrievalService = artifactRetrievalService;
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
        // This v1 REST submit path is tacticl-only, so the product is hardcoded here. The Phase-1
        // dispatch front door (Discord→PDLC) will supply the product from the entry-point registry.
        PipelineRun run = pdlcV2Service.submitPipeline(
            "tacticl", user.getUserId(), sparkId,
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

    @PutMapping("/skip-roles")
    public ResponseEntity<PipelineRunDto> updateSkipRoles(
            @AuthUser AuthenticatedUser user,
            @PathVariable String sparkId,
            @RequestBody java.util.List<String> skipRoles) {
        try {
            return ResponseEntity.ok(PipelineRunDto.from(
                pdlcV2Service.updateSkipRoles(user.getUserId(), sparkId, skipRoles)));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping("/artifacts/{role}")
    public ResponseEntity<RoleArtifactDto> getArtifact(
            @AuthUser AuthenticatedUser user,
            @PathVariable String sparkId,
            @PathVariable String role) {
        PipelineRun run = pdlcV2Service.getStatus(user.getUserId(), sparkId)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Pipeline run not found for spark: " + sparkId));
        if (run.getArtifacts() == null || run.getArtifacts().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // Find artifact by role (key format: "phase_role" or just "role")
        String artifactPath = null;
        for (var entry : run.getArtifacts().entrySet()) {
            if (entry.getKey().endsWith("_" + role) || entry.getKey().equals(role)) {
                artifactPath = entry.getValue();
                break;
            }
        }
        if (artifactPath == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(new RoleArtifactDto(
            role, "text",
            java.util.Map.of("artifactPath", artifactPath),
            1
        ));
    }

    /**
     * Manifest for the PDLC Artifacts viewer: a grouped rail of every expected role artifact,
     * enriched from the committed {@code .tacticl/pdlc/{runId}/manifest.json} (authoritative
     * {@code path}/{@code title}/{@code present}) and the run's per-role status.
     *
     * <p>Never 500s and never 404s for the no-data case: with no run, no artifacts, or GitHub
     * disabled it returns the canonical skeleton (all {@code pending}, {@code present=false}).
     * When the run exists but is unknown to this user it returns an empty list rather than leaking
     * existence.
     */
    @GetMapping("/artifacts")
    public ResponseEntity<List<ArtifactManifestEntryDto>> listArtifacts(
            @AuthUser AuthenticatedUser user,
            @PathVariable String sparkId) {
        PipelineRun run = pdlcV2Service.getStatus(user.getUserId(), sparkId).orElse(null);
        if (run == null) {
            // No run yet (or not this user's): emit the canonical skeleton so the rail can render
            // its pending groups without a 404.
            return ResponseEntity.ok(ArtifactManifestEntryDto.build(null, java.util.List.of()));
        }
        return ResponseEntity.ok(
            ArtifactManifestEntryDto.build(run, artifactRetrievalService.listArtifacts(run)));
    }

    /**
     * Return the decoded markdown body of a single PDLC artifact by its file stem
     * (the {@code name} field of a manifest entry — e.g. {@code product-brief},
     * {@code architecture}, {@code plan}, {@code change-summary}, {@code review},
     * {@code test-report}, {@code security-report}; see {@link io.tacticl.service.pipeline.dto.PdlcArtifactCatalog}).
     *
     * <p>Returns {@code 404} (never {@code 500}) when GitHub is disabled or the file has not been
     * committed to the repo yet; returns the decoded UTF-8 markdown + blob sha otherwise.
     */
    @GetMapping("/artifacts/{name}/content")
    public ResponseEntity<ArtifactContentDto> getArtifactContent(
            @AuthUser AuthenticatedUser user,
            @PathVariable String sparkId,
            @PathVariable String name) {
        PipelineRun run = pdlcV2Service.getStatus(user.getUserId(), sparkId)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Pipeline run not found for spark: " + sparkId));
        return artifactRetrievalService.readArtifact(run, name)
                .map(c -> ResponseEntity.ok(new ArtifactContentDto(c.name(), c.markdown(), c.sha())))
                .orElse(ResponseEntity.<ArtifactContentDto>notFound().build());
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
