package io.tacticl.business.voice;

import java.util.List;

/**
 * Everything a {@link ConversationEngine} needs to answer one turn.
 *
 * @param productId data-scoping key for the arbiter
 * @param userId    resolved tacticl user id
 * @param sessionId voice session id (transcript linkage)
 * @param turnId    idempotency id for this turn
 * @param userText  the final user transcript
 * @param history   this session's prior turns, oldest first (memory)
 * @param canDispatch whether THIS turn's caller holds dispatch authority (an owner channel, or admin
 *                  of the governing EntryPoint). Threaded to the arbiter so its alignment gate fails
 *                  CLOSED for a non-admin "yes" — a non-admin can converse but never authorize a build.
 */
public record ConversationContext(String productId,
                                  String userId,
                                  String sessionId,
                                  String turnId,
                                  String userText,
                                  List<VoiceSession.Utterance> history,
                                  boolean canDispatch) {
}
