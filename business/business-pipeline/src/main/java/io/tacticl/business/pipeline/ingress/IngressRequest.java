package io.tacticl.business.pipeline.ingress;

import java.util.List;

/**
 * Transport-neutral inbound request to the pipeline front door. Every channel adapter
 * (Discord, Telegram, WEB, voice) normalizes its native payload into one of these; the
 * {@code IngressDispatchService} then resolves the {@code EntryPoint}, authorizes, and routes
 * purely on {@link #kind()} — it never sees channel-specific shapes.
 *
 * <p>Normalization is pure: adapters do not perform I/O when building this record. Attachment bytes
 * are referenced (see {@link Attachment}) and materialized later by the dispatcher.
 *
 * @param origin        where it came from / where updates go (channel + routing key + handles)
 * @param tacticlUserId resolved internal user id (hard precondition for triggers/decisions —
 *                      an unlinked channel identity must resolve to {@code null} and be rejected,
 *                      never dispatched)
 * @param kind          what the dispatcher should do with this request
 * @param text          free-text body (the spark request for EXPLICIT_TRIGGER, the turn for
 *                      CONVERSATION_TURN); may be null/blank for pure CHECKPOINT_DECISION/CANCEL_RUN
 * @param attachments   inbound binaries by reference; never null (empty list when none)
 * @param productHint   optional caller-supplied product hint; the resolved EntryPoint's
 *                      {@code productId} is authoritative, this is advisory only. Nullable.
 * @param correlationId transport-native idempotency/trace key (e.g. Discord interaction id);
 *                      used for dedup upstream and log correlation. Nullable.
 * @param decision      checkpoint decision payload; required for CHECKPOINT_DECISION, null otherwise
 */
public record IngressRequest(
    RunOrigin origin,
    String tacticlUserId,
    IngressKind kind,
    String text,
    List<Attachment> attachments,
    String productHint,
    String correlationId,
    CheckpointDecisionPayload decision
) {
    public IngressRequest {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
