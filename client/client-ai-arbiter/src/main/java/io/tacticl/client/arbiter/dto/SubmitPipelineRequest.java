package io.tacticl.client.arbiter.dto;

import java.util.List;
import java.util.Map;

public record SubmitPipelineRequest(
    String product,
    String pipelineRunId,
    String sparkId,
    String userId,
    String playbook,
    String sparkRequest,
    String repoUrl,
    String githubToken,
    List<String> skipRoles,
    double costCeilingUsd,
    String callbackUrl,
    Map<String, String> roleIdentities,
    String playbookConfigJson,
    String knowledgeNamespace,
    Map<String, Integer> roleTtlSeconds
) {}
