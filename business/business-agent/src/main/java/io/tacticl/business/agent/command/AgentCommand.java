package io.tacticl.business.agent.command;

import io.tacticl.data.sparks.entity.SparkInitiatorSource;

/**
 * Single input shape for the agent orchestration core. All channels (HTTP,
 * Telegram, future) build one of these and hand it to {@link AgentCommandService}.
 *
 * <p>Nullable fields:
 * <ul>
 *   <li>{@code model} — null lets the service apply the default (Sonnet for
 *       non-pipeline path).</li>
 *   <li>{@code costCeilingUsd} — null lets the service apply the documented
 *       default ($50 today; future {@code UserConfig} lookup belongs in the
 *       service).</li>
 *   <li>{@code initiatorSource} — null is treated as direct HTTP user.</li>
 *   <li>{@code projectId} — only set for channel-scoped sparks (Telegram
 *       group, future Slack channel).</li>
 *   <li>{@code repoUrl} — pipeline router accepts null as "no repo".</li>
 * </ul>
 */
public record AgentCommand(
        String userId,
        String text,
        String model,
        Double costCeilingUsd,
        SparkInitiatorSource initiatorSource,
        String projectId,
        String repoUrl) {

    public static AgentCommand fromHttp(String userId, String text, String model) {
        return new AgentCommand(userId, text, model, null, null, null, null);
    }

    public static AgentCommand fromTelegramGroup(String userId, String text,
                                                  String projectId, String repoUrl) {
        return new AgentCommand(userId, text, null, null,
                SparkInitiatorSource.TELEGRAM_GROUP, projectId, repoUrl);
    }
}
