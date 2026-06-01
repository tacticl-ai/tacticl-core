package io.tacticl.business.pipeline.ingress;

import io.tacticl.data.pipeline.entity.CheckpointDecision;

/**
 * The decision an admin made on an open checkpoint, carried on a
 * {@link IngressKind#CHECKPOINT_DECISION} request. Maps a channel-native button press
 * (Approve / Request changes / Reject) onto the existing {@link CheckpointDecision} verbs the
 * arbiter resume RPC understands.
 *
 * <ul>
 *   <li>{@code sparkId} — the spark whose latest run owns the checkpoint (the existing
 *       {@code PdlcV2Service.resolveCheckpoint} signature is spark-scoped).</li>
 *   <li>{@code checkpointId} — the specific open checkpoint being resolved.</li>
 *   <li>{@code decision} — APPROVED / REWORK / CANCEL.</li>
 *   <li>{@code feedback} — optional free-text the admin supplied with a "request changes"; nullable.</li>
 * </ul>
 */
public record CheckpointDecisionPayload(
    String sparkId,
    String checkpointId,
    CheckpointDecision decision,
    String feedback
) {}
