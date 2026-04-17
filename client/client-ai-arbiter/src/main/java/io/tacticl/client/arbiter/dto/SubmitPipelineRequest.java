package io.tacticl.client.arbiter.dto;

import java.util.List;

public record SubmitPipelineRequest(
    String pipelineRunId,
    String sparkId,
    String userId,
    String playbook,
    String sparkRequest,
    String repoUrl,
    String githubToken,
    List<String> skipRoles,
    double costCeilingUsd,
    String callbackUrl
) {}
