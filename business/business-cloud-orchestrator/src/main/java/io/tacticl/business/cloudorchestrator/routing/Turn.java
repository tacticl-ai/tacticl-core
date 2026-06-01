package io.tacticl.business.cloudorchestrator.routing;

/**
 * Minimal placeholder for the embedded conversation turn entity.
 *
 * <p>This record captures ONLY the fields {@link PersonaRouter} reads — enough to
 * keep the router compilable independently of the data layer's progress.
 *
 * <p>TODO: REPLACE WITH actual {@code io.tacticl.data.cloudorchestrator.entity.Turn}
 * after Wave 2 Agent 1 (data-cloud-orchestrator) lands the full entity per SAD §9.5.
 * When swapped, the router signature changes from this placeholder to the data entity;
 * call sites compile unchanged because field accessors (text(), role(), personaId())
 * match the spec.
 *
 * @param role        "user" or "assistant"
 * @param personaId   set for assistant turns, null for user turns
 * @param text        the transcript text (post-STT for voice turns)
 */
public record Turn(String role, String personaId, String text) {
}
