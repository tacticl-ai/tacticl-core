package io.tacticl.data.cloudorchestrator.entity;

/**
 * Persona family — drives the execution path (per SAD §4.5).
 *
 * <ul>
 *   <li>{@link #CONVERSATIONAL} — in-JVM Anthropic call via {@code InvokePersonaActivity}.
 *       Chat-plane personas (Product Manager, Market Researcher) that speak to the user
 *       in ~1s turns.</li>
 *   <li>{@link #PDLC} — runs inside an ephemeral Docker container on Hetzner via Arbiter.
 *       Code-generation roles needing workspace isolation, Claude Code CLI, repo access,
 *       and MCP tools.</li>
 * </ul>
 *
 * <p>The legacy {@code UTILITY} family was removed before v1; do not re-introduce it
 * here without a corresponding spec update.
 */
public enum PersonaFamily {
    CONVERSATIONAL,
    PDLC
}
