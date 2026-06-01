package io.tacticl.application.temporal.smoke;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * No-op workflow that proves the Temporal SDK is wired correctly end-to-end.
 *
 * <p>Registered on {@code cloud-agent-session-tq}. The result is a constant
 * {@code "pong"} — no I/O, no activities. Used by {@code SmokeWorkflowTest} and
 * can be invoked manually against a live cluster for environment validation.
 */
@WorkflowInterface
public interface SmokeWorkflow {

    @WorkflowMethod
    String ping();
}
