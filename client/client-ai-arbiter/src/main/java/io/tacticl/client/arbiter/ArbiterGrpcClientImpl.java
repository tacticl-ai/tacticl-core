package io.tacticl.client.arbiter;

import cidadel.ai.arbiter.pipeline.v1.ArbiterPipelineServiceGrpc;
import cidadel.ai.arbiter.pipeline.v1.CancelPipelineRequest;
import cidadel.ai.arbiter.pipeline.v1.GetPipelineResultRequest;
import cidadel.ai.arbiter.pipeline.v1.GetPipelineResultResponse;
import cidadel.ai.arbiter.pipeline.v1.ResolveCheckpointRequest;
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
    private static final String PRODUCT = "tacticl";

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
                .setProduct(PRODUCT)
                .setPipelineName(request.playbook())
                .setRequestContextJson(contextJson)
                .setRegistryBasePath(registryBasePath)
                .setCallbackUrl(request.callbackUrl())
                .setGithubToken(request.githubToken() != null ? request.githubToken() : "")
                .setUserId(request.userId() != null ? request.userId() : "")
                .setRepoUrl(request.repoUrl() != null ? request.repoUrl() : "")
                .setKnowledgeNamespace(request.knowledgeNamespace() != null ? request.knowledgeNamespace() : "")
                .setPlaybookConfigJson(request.playbookConfigJson() != null ? request.playbookConfigJson() : "");

        if (request.roleIdentities() != null) {
            builder.putAllRoleIdentities(request.roleIdentities());
        }
        if (request.roleTtlSeconds() != null) {
            builder.putAllRoleTtlSeconds(request.roleTtlSeconds());
        }

        cidadel.ai.arbiter.pipeline.v1.SubmitPipelineRequest protoReq = builder.build();

        log.info("Submitting pipeline to arbiter: runId={} playbook={}", request.pipelineRunId(), request.playbook());
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
        GetPipelineResultResponse protoResp = stub.getPipelineResult(protoReq);
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

    private String buildContextJson(SubmitPipelineRequest request) {
        Map<String, Object> context = new HashMap<>();
        context.put("pipelineRunId", request.pipelineRunId());
        context.put("sparkId", request.sparkId());
        context.put("sparkRequest", request.sparkRequest());
        context.put("skipRoles", request.skipRoles() != null ? request.skipRoles() : List.of());
        context.put("costCeilingUsd", request.costCeilingUsd());
        try {
            return mapper.writeValueAsString(context);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize pipeline context to JSON", e);
        }
    }
}
