package io.tacticl.client.arbiter;

import cidadel.ai.arbiter.pipeline.v1.ArbiterPipelineServiceGrpc;
import cidadel.ai.arbiter.pipeline.v1.CancelPipelineRequest;
import cidadel.ai.arbiter.pipeline.v1.GetPipelineResultRequest;
import cidadel.ai.arbiter.pipeline.v1.GetPipelineResultResponse;
import cidadel.ai.arbiter.pipeline.v1.ResolveCheckpointRequest;
import cidadel.ai.arbiter.pipeline.v1.SignalPipelineDecisionRequest;
import cidadel.ai.arbiter.pipeline.v1.SignalPipelineDecisionResponse;
import io.tacticl.client.arbiter.dto.PipelineResultResponse;
import io.tacticl.client.arbiter.dto.SubmitPipelineRequest;
import io.tacticl.client.arbiter.dto.SubmitPipelineResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Real gRPC client for cidadel-ai-arbiter.
 * Maps our domain DTOs to the arbiter proto messages.
 * Registered by ArbiterClientConfig when pdlc.v2.arbiter.host is set.
 */
public class ArbiterGrpcClientImpl implements ArbiterPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ArbiterGrpcClientImpl.class);

    private final ArbiterPipelineServiceGrpc.ArbiterPipelineServiceBlockingStub stub;
    private final String registryBasePath;
    private final JsonMapper mapper;

    public ArbiterGrpcClientImpl(
            ArbiterPipelineServiceGrpc.ArbiterPipelineServiceBlockingStub stub,
            String registryBasePath) {
        this.stub = stub;
        this.registryBasePath = registryBasePath;
        this.mapper = JsonMapper.builder().build();
    }

    @Override
    public SubmitPipelineResponse submitPipeline(SubmitPipelineRequest request) {
        String contextJson = buildContextJson(request);

        cidadel.ai.arbiter.pipeline.v1.SubmitPipelineRequest.Builder builder =
            cidadel.ai.arbiter.pipeline.v1.SubmitPipelineRequest.newBuilder()
                .setProduct(request.product())
                .setPipelineName(request.playbook())
                .setRequestContextJson(contextJson)
                .setRegistryBasePath(registryBasePath)
                .setCallbackUrl(request.callbackUrl())
                .setGithubToken(request.githubToken() != null ? request.githubToken() : "")
                .setUserId(request.userId() != null ? request.userId() : "")
                .setRepoUrl(request.repoUrl() != null ? request.repoUrl() : "")
                .setKnowledgeNamespace(request.knowledgeNamespace() != null ? request.knowledgeNamespace() : "")
                .setPlaybookConfigJson(request.playbookConfigJson() != null ? request.playbookConfigJson() : "")
                // Idempotency key = the pipelineRunId. On the Temporal path the arbiter derives
                // workflowId = pdlc-{idempotency_key} (REJECT_DUPLICATE) and echoes it back as the
                // pipeline_id we store + correlate callbacks by. On the legacy path it's ignored.
                .setIdempotencyKey(request.pipelineRunId() != null ? request.pipelineRunId() : "");

        if (request.roleIdentities() != null) {
            builder.putAllRoleIdentities(request.roleIdentities());
        }
        if (request.roleTtlSeconds() != null) {
            builder.putAllRoleTtlSeconds(request.roleTtlSeconds());
        }

        cidadel.ai.arbiter.pipeline.v1.SubmitPipelineRequest protoReq = builder.build();

        log.info("Submitting pipeline to arbiter: runId={} playbook={}", request.pipelineRunId(), request.playbook());
        // HARD-FAILURE INVARIANT: this is the ONLY submit path. A gRPC error here
        // (e.g. FAILED_PRECONDITION when the arbiter refuses to route the tenant onto the
        // Temporal path) MUST propagate as a StatusRuntimeException to the originating channel.
        // Never catch-and-fall-back to a legacy / in-JVM submit, and never retry onto a
        // non-Temporal path — the channel (the gRPC ManagedChannel) carries no retry policy by
        // design. Locked by ArbiterGrpcClientImplTest.submitPipeline_grpcFailedPrecondition_propagates.
        cidadel.ai.arbiter.pipeline.v1.SubmitPipelineResponse protoResp =
            stub.withDeadlineAfter(10, TimeUnit.SECONDS).submitPipeline(protoReq);
        log.info("Arbiter accepted pipeline: arbiterPipelineId={} status={}", protoResp.getPipelineId(), protoResp.getStatus());

        return new SubmitPipelineResponse(request.pipelineRunId(), protoResp.getPipelineId(), protoResp.getStatus());
    }

    @Override
    public void cancelPipeline(String arbiterPipelineId) {
        CancelPipelineRequest protoReq = CancelPipelineRequest.newBuilder()
            .setPipelineId(arbiterPipelineId)
            .build();
        log.info("Cancelling pipeline in arbiter: arbiterPipelineId={}", arbiterPipelineId);
        stub.withDeadlineAfter(10, TimeUnit.SECONDS).cancelPipeline(protoReq);
    }

    @Override
    public PipelineResultResponse getResult(String arbiterPipelineId) {
        GetPipelineResultRequest protoReq = GetPipelineResultRequest.newBuilder()
            .setPipelineId(arbiterPipelineId)
            .build();
        GetPipelineResultResponse protoResp = stub.withDeadlineAfter(10, TimeUnit.SECONDS).getPipelineResult(protoReq);
        return new PipelineResultResponse(
            protoResp.getPipelineId(),
            protoResp.getStatus(),
            protoResp.getResultJson().isEmpty() ? null : protoResp.getResultJson(),
            protoResp.getErrorMessage().isEmpty() ? null : protoResp.getErrorMessage(),
            protoResp.getDurationMs(),
            protoResp.getTotalTokens(),
            protoResp.getEstimatedCostUsd()
        );
    }

    @Override
    public void resolveCheckpoint(String arbiterPipelineId, String checkpointId,
                                  String decision, String feedback) {
        ResolveCheckpointRequest protoReq = ResolveCheckpointRequest.newBuilder()
            .setPipelineId(arbiterPipelineId)
            .setCheckpointId(checkpointId)
            .setDecision(decision)
            .setFeedback(feedback != null ? feedback : "")
            .build();
        log.info("Resolving checkpoint in arbiter: arbiterPipelineId={} checkpointId={} decision={}",
                 arbiterPipelineId, checkpointId, decision);
        stub.withDeadlineAfter(10, TimeUnit.SECONDS).resolveCheckpoint(protoReq);
    }

    @Override
    public void signalDecision(String workflowId, String askId, String decision,
                               String approvedSha, String gateNonce, String approver, String reason) {
        SignalPipelineDecisionRequest protoReq = SignalPipelineDecisionRequest.newBuilder()
            .setWorkflowId(workflowId != null ? workflowId : "")
            .setAskId(askId != null ? askId : "")
            .setDecision(decision != null ? decision : "")
            .setApprovedSha(approvedSha != null ? approvedSha : "")
            .setGateNonce(gateNonce != null ? gateNonce : "")
            .setApprover(approver != null ? approver : "")
            .setReason(reason != null ? reason : "")
            .build();
        log.info("Signalling pipeline decision: workflowId={} askId={} decision={}",
                 workflowId, askId, decision);
        SignalPipelineDecisionResponse resp =
            stub.withDeadlineAfter(10, TimeUnit.SECONDS).signalPipelineDecision(protoReq);
        if (!resp.getAccepted()) {
            log.warn("Arbiter did NOT accept pipeline decision workflowId={} askId={}: {}",
                     workflowId, askId, resp.getReason());
        }
    }

    private String buildContextJson(SubmitPipelineRequest request) {
        Map<String, Object> context = new HashMap<>();
        // Keys use snake_case to match Handlebars template variables ({{spark_request}}, etc.)
        context.put("pipeline_run_id", request.pipelineRunId());
        context.put("spark_id", request.sparkId());
        context.put("spark_request", request.sparkRequest());
        // The arbiter workspace assembler writes context/user-prompt.md ONLY from the
        // camelCase `userPrompt` key (workspace-assembler.ts) — so the agent gets a clean
        // prompt file with the report instead of having to dig it out of pipeline-config.json.
        // Kept alongside snake_case spark_request (Handlebars {{spark_request}} + back-compat).
        context.put("userPrompt", request.sparkRequest());
        context.put("skip_roles", request.skipRoles() != null ? request.skipRoles() : List.of());
        context.put("cost_ceiling_usd", request.costCeilingUsd());
        try {
            return mapper.writeValueAsString(context);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize pipeline context to JSON", e);
        }
    }
}
