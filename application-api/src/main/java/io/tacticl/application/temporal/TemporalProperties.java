package io.tacticl.application.temporal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds Temporal connection + task-queue config under {@code tacticl.temporal.*}.
 *
 * <p>Per SAD §3.1, v1 uses plain trust on the private network (no mTLS).
 * Per SAD §3.2, three task queues are used — one per workflow type.
 *
 * <p>Property mappings (Spring relaxed binding):
 * <ul>
 *   <li>{@code tacticl.temporal.host} → {@link #host()}</li>
 *   <li>{@code tacticl.temporal.port} → {@link #port()}</li>
 *   <li>{@code tacticl.temporal.namespace} → {@link #namespace()}</li>
 *   <li>{@code tacticl.temporal.task-queues.cloud-agent-session} →
 *       {@code taskQueues().cloudAgentSession()}</li>
 *   <li>{@code tacticl.temporal.task-queues.pipeline} →
 *       {@code taskQueues().pipeline()}</li>
 *   <li>{@code tacticl.temporal.task-queues.voice-activity} →
 *       {@code taskQueues().voiceActivity()}</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "tacticl.temporal")
public record TemporalProperties(
        String host,
        int port,
        String namespace,
        TaskQueues taskQueues) {

    public TemporalProperties {
        if (host == null || host.isBlank()) {
            host = "localhost";
        }
        if (port <= 0) {
            port = 7233;
        }
        if (namespace == null || namespace.isBlank()) {
            namespace = "tacticl-qa";
        }
        if (taskQueues == null) {
            taskQueues = TaskQueues.defaults();
        }
    }

    public String target() {
        return host + ":" + port;
    }

    public record TaskQueues(
            String cloudAgentSession,
            String pipeline,
            String voiceActivity) {

        public TaskQueues {
            if (cloudAgentSession == null || cloudAgentSession.isBlank()) {
                cloudAgentSession = "cloud-agent-session-tq";
            }
            if (pipeline == null || pipeline.isBlank()) {
                pipeline = "pipeline-tq";
            }
            if (voiceActivity == null || voiceActivity.isBlank()) {
                voiceActivity = "voice-activity-tq";
            }
        }

        public static TaskQueues defaults() {
            return new TaskQueues(
                    "cloud-agent-session-tq",
                    "pipeline-tq",
                    "voice-activity-tq");
        }
    }
}
