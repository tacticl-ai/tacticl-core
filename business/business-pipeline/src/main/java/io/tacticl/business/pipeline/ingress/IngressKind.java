package io.tacticl.business.pipeline.ingress;

/**
 * What an inbound ingress request is asking the pipeline front door to do. The
 * {@code IngressDispatchService} routes purely on this discriminator:
 *
 * <ul>
 *   <li>{@link #CONVERSATION_TURN} — a free-text turn that has not (yet) been promoted to a run;
 *       handed to the conversation subsystem.</li>
 *   <li>{@link #EXPLICIT_TRIGGER} — an admin explicitly asked to kick off a PDLC run
 *       (slash command / "Send to PDLC" context menu); creates a Spark then submits a pipeline.</li>
 *   <li>{@link #CHECKPOINT_DECISION} — an Approve / Request-changes / Reject decision on an open
 *       checkpoint; resolves the checkpoint and resumes the arbiter run.</li>
 *   <li>{@link #CANCEL_RUN} — cancel an in-flight run.</li>
 * </ul>
 */
public enum IngressKind {
    CONVERSATION_TURN,
    EXPLICIT_TRIGGER,
    CHECKPOINT_DECISION,
    CANCEL_RUN
}
