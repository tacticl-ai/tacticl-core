package io.tacticl.business.agent.command;

import java.util.List;

/**
 * Single output shape from {@link AgentCommandService}. Adapters map this to
 * their channel-native response (HTTP {@code AgentCommandResponse}, Telegram
 * outbound text). All fields except {@code sparkId} are nullable: pipeline
 * runs return {@code pipelineRunId} + tier; sync cloud runs return
 * {@code responseText} + token cost; failures return {@code succeeded=false}.
 */
public record AgentCommandResult(
        String sparkId,
        String sparkStatus,
        String executionMode,
        String pipelineRunId,
        String pipelineTier,
        String responseText,
        List<Object> actions,
        boolean succeeded,
        String model,
        int tokensUsed) {

    public static AgentCommandResult pipeline(String sparkId, String runId, String tier) {
        return new AgentCommandResult(sparkId, "EXECUTING", "ASYNC",
                runId, tier, null, List.of(), true, null, 0);
    }

    public static AgentCommandResult cloudCompleted(String sparkId, String text, String model, int tokens) {
        return new AgentCommandResult(sparkId, "COMPLETED", "SYNC",
                null, "SIMPLE", text, List.of(), true, model, tokens);
    }

    public static AgentCommandResult cloudFailed(String sparkId, String message) {
        return new AgentCommandResult(sparkId, "FAILED", "SYNC",
                null, "SIMPLE", message, List.of(), false, null, 0);
    }
}
