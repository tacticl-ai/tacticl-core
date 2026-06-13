package io.tacticl.client.arbiter.conversation;

/**
 * One of the user's in-flight pipelines, supplied as grounding to
 * {@code ArbiterConversationService.ConverseTurn} so the conversational persona
 * knows what's building and can answer "how's it going / has the PM finished?"
 * without a tool call. Maps onto the proto {@code PipelineRef}; provider-neutral.
 *
 * @param pipelineRunId      the run id (the persona refers to runs by name, not id)
 * @param name               human-facing pipeline/playbook name
 * @param status             "RUNNING" | "BLOCKED" | "COMPLETED" | "FAILED" | ...
 * @param currentRole        the role currently working (e.g. "implementer"); may be blank
 * @param blockedCheckpointId if blocked at a human gate, the open checkpoint id; else blank
 */
public record ConvPipelineRef(String pipelineRunId,
                              String name,
                              String status,
                              String currentRole,
                              String blockedCheckpointId) {
}
