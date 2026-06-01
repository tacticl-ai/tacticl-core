package io.tacticl.application.temporal.smoke;

/**
 * Constant implementation — returns {@code "pong"}. Deterministic, no I/O,
 * safe for {@code TestWorkflowEnvironment}.
 */
public class SmokeWorkflowImpl implements SmokeWorkflow {

    @Override
    public String ping() {
        return "pong";
    }
}
