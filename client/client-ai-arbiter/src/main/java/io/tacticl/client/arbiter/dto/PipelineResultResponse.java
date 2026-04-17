package io.tacticl.client.arbiter.dto;

public record PipelineResultResponse(
    String pipelineId,
    String status,
    String resultJson,
    String errorMessage,
    long durationMs,
    int totalTokens,
    double estimatedCostUsd
) {}
