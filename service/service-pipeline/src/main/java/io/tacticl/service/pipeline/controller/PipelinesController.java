package io.tacticl.service.pipeline.controller;

import io.cidadel.framework.authorization.annotation.AuthUser;
import io.cidadel.framework.authorization.context.AuthenticatedUser;
import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.pipeline.service.PdlcV2Service;
import io.tacticl.service.pipeline.dto.PipelineRunSummaryDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * Dashboard-facing list of the authenticated user's pipeline runs.
 *
 * <p>Sibling of {@link PipelineController} (which is spark-scoped); this controller is the
 * cross-spark list endpoint the Dashboard uses to render all of a user's pipelines.
 */
@RestController
@RequestMapping("/v1/pipelines")
public class PipelinesController extends BaseController {

    private final PdlcV2Service pdlcV2Service;

    public PipelinesController(PdlcV2Service pdlcV2Service) {
        this.pdlcV2Service = pdlcV2Service;
    }

    @Override
    protected String getModuleName() { return "pipelines"; }

    @GetMapping
    public ResponseEntity<List<PipelineRunSummaryDto>> listPipelines(
            @AuthUser AuthenticatedUser user) {
        List<PipelineRunSummaryDto> runs = pdlcV2Service.listRuns(user.getUserId()).stream()
                .map(PipelineRunSummaryDto::from)
                .toList();
        return ResponseEntity.ok(runs);
    }
}
