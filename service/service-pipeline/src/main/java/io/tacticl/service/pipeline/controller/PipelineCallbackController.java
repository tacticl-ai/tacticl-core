package io.tacticl.service.pipeline.controller;

import io.cidadel.service.base.controller.BaseController;
import io.tacticl.business.pipeline.dto.PipelineCallbackEvent;
import io.tacticl.business.pipeline.service.PdlcV2Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint — receives HTTP push events from cidadel-ai-arbiter.
 * Protected by VPC firewall rules (Hetzner IP range only).
 * Optionally protected by shared secret via X-Arbiter-Secret header.
 */
@RestController
@RequestMapping("/v1/internal/pipeline")
public class PipelineCallbackController extends BaseController {

    @Override
    protected String getModuleName() { return "pipeline-callback"; }

    private final PdlcV2Service pdlcV2Service;
    private final String callbackSecret;

    public PipelineCallbackController(
            PdlcV2Service pdlcV2Service,
            @Value("${pdlc.v2.callback.secret:}") String callbackSecret) {
        this.pdlcV2Service = pdlcV2Service;
        this.callbackSecret = callbackSecret;
    }

    @PostMapping("/callback")
    public ResponseEntity<Void> handleCallback(
            @RequestHeader(value = "X-Arbiter-Secret", required = false) String incomingSecret,
            @RequestBody PipelineCallbackEvent event) {
        if (!callbackSecret.isBlank() && !callbackSecret.equals(incomingSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        pdlcV2Service.handleCallbackEvent(event);
        return ResponseEntity.ok().build();
    }
}
