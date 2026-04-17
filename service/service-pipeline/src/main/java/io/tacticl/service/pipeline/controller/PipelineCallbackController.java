package io.tacticl.service.pipeline.controller;

import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.pipeline.service.PipelineEventEmitter;
import io.tacticl.service.pipeline.dto.PipelineCallbackEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint — receives HTTP push events from cidadel-ai-arbiter.
 * Protected by VPC firewall rules (Hetzner IP range only).
 */
@RestController
@RequestMapping("/v1/internal/pipeline")
public class PipelineCallbackController extends BaseController {

    @Override
    protected String getModuleName() { return "pipeline-callback"; }

    private static final String PIPELINE_COMPLETED = "PIPELINE_COMPLETED";
    private static final String PIPELINE_FAILED = "PIPELINE_FAILED";
    private static final String PIPELINE_CANCELLED = "PIPELINE_CANCELLED";

    private final PipelineEventEmitter pipelineEventEmitter;

    public PipelineCallbackController(PipelineEventEmitter pipelineEventEmitter) {
        this.pipelineEventEmitter = pipelineEventEmitter;
    }

    @PostMapping("/callback")
    public ResponseEntity<Void> handleCallback(@RequestBody PipelineCallbackEvent event) {
        // TODO: Persist event to PipelineEventRepository when arbiter integration goes live.
        // Currently events are only fanned out via SSE; if no subscriber is connected, the event is lost.
        pipelineEventEmitter.emit(event.pipelineRunId(), event.eventType(), event.payloadJson());

        if (PIPELINE_COMPLETED.equals(event.eventType())
                || PIPELINE_FAILED.equals(event.eventType())
                || PIPELINE_CANCELLED.equals(event.eventType())) {
            pipelineEventEmitter.completeAll(event.pipelineRunId());
        }

        return ResponseEntity.ok().build();
    }
}
