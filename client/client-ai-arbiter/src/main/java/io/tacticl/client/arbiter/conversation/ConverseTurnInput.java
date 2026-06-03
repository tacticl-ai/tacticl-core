package io.tacticl.client.arbiter.conversation;

import java.util.List;

/**
 * Domain input for one {@code ArbiterConversationService.ConverseTurn} call — a
 * single conversational persona turn. Proto-free so callers (the voice plane)
 * never touch generated types.
 *
 * @param productId   data-scoping key (e.g. "tacticl")
 * @param userId      resolved tacticl user id (already linked/authorized)
 * @param sessionId   caller voice session id (transcript linkage, not routing)
 * @param turnId      client-generated idempotency id for the turn
 * @param text        the final user transcript for this turn
 * @param personaHint optional persona id override; blank/null → the arbiter routes
 * @param history     prior turns this session, oldest first (may be empty)
 * @param locale      optional BCP-47 locale; may be null
 */
public record ConverseTurnInput(String productId,
                                String userId,
                                String sessionId,
                                String turnId,
                                String text,
                                String personaHint,
                                List<ConvTurn> history,
                                String locale) {
}
