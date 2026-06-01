package io.tacticl.business.cloudorchestrator.routing;

/**
 * Minimal placeholder snapshot of the currently-focused pipeline, as observed by
 * the router. The router takes this as an {@code Optional} input but the v1
 * algorithm (SAD §7.1) does not branch on its contents — the session-level
 * {@code PIPELINE_BLOCKED} status already captures the only routing-relevant
 * fact. The parameter is kept on the signature so the LLM-fallback step from
 * §7.3 can extend the function without changing the caller.
 *
 * <p>TODO: REPLACE WITH the actual pipeline projection type when
 * Wave 2 Agent 1 / Phase 6 lands {@code PipelineRun} per SAD §9.2.
 */
public record PipelineState(String pipelineRunId, String status) {
}
